#!/usr/bin/env bash
set -euo pipefail

# Imports the public phone_location dataset into the application's prefix index.
# The upstream SQL is intentionally downloaded at deploy time (not vendored).
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-ycsopen_migrator}"
DB_PASSWORD="${DB_PASSWORD:-ycsopen_migrator}"
DB_NAME="${DB_NAME:-ycsopen_sms}"
URL="${PHONE_LOCATION_URL:-https://raw.githubusercontent.com/dannyhu926/phone_location/master/mysql/phone_location.sql}"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
curl --fail --location --retry 3 "$URL" -o "$tmp"

mysql=(mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --password="$DB_PASSWORD" "$DB_NAME")
"${mysql[@]}" < "$tmp"
"${mysql[@]}" <<'SQL'
INSERT INTO number_prefix_versions(version_no,update_type,status,source_name,total_rows,actor,activated_at)
SELECT 'PHONE_LOCATION_2026','FULL','ACTIVE','dannyhu926/phone_location','0','phone-location-import',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM number_prefix_versions WHERE version_no='PHONE_LOCATION_2026');
INSERT IGNORE INTO number_prefix_mappings(version_id,prefix,carrier,province,city,source_name)
SELECT v.id, LEFT(p.phone,7), CASE p.isp_type WHEN 1 THEN 'MOBILE' WHEN 2 THEN 'UNICOM' WHEN 3 THEN 'TELECOM' ELSE 'UNKNOWN' END,
       COALESCE(p.province,'未知'), COALESCE(p.city,'未知'), 'dannyhu926/phone_location'
FROM phone_location p JOIN number_prefix_versions v ON v.version_no='PHONE_LOCATION_2026'
WHERE p.isp_type IN (1,2,3)
GROUP BY v.id, LEFT(p.phone,7), p.isp_type, p.province, p.city;
UPDATE number_prefix_versions v SET total_rows=(SELECT COUNT(*) FROM number_prefix_mappings m WHERE m.version_id=v.id)
WHERE v.version_no='PHONE_LOCATION_2026';
SQL
echo "phone location prefixes imported from $URL"
