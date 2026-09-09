#!/usr/bin/env bash
# 初始化本地 MySQL 数据库（开发环境用）。
# 用法: ./init-db.sh [db_name] [app_user] [app_password] [migration_user] [migration_password]
set -euo pipefail
DB_NAME="${1:-ycsopen_sms}"
DB_USER="${2:-ycsopen}"
DB_PASS="${3:-ycsopen}"
MIGRATION_USER="${4:-ycsopen_migrator}"
MIGRATION_PASS="${5:-ycsopen_migrator}"

for value in "$DB_NAME" "$DB_USER" "$MIGRATION_USER"; do
  if [[ ! "$value" =~ ^[A-Za-z0-9_]{1,64}$ ]]; then
    echo "database and user names may contain only letters, digits, and underscores" >&2
    exit 2
  fi
done
for value in "$DB_PASS" "$MIGRATION_PASS"; do
  if [[ ! "$value" =~ ^[A-Za-z0-9_.@%+=!-]{1,128}$ ]]; then
    echo "database passwords contain unsupported characters for this local helper" >&2
    exit 2
  fi
done

BINLOG_POLICY="$(mysql -u root -Nse "SELECT CONCAT(@@GLOBAL.log_bin, ':', @@GLOBAL.log_bin_trust_function_creators);")"
if [[ "$BINLOG_POLICY" == "1:0" ]]; then
  echo "MySQL binary logging is enabled but log_bin_trust_function_creators is OFF." >&2
  echo "Ask the DBA to enable log_bin_trust_function_creators before Flyway migration V1500." >&2
  exit 3
fi

mysql -u root -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -e "CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';"
mysql -u root -e "CREATE USER IF NOT EXISTS '${MIGRATION_USER}'@'%' IDENTIFIED BY '${MIGRATION_PASS}';"
mysql -u root -e "REVOKE ALL PRIVILEGES ON \`${DB_NAME}\`.* FROM '${DB_USER}'@'%';" 2>/dev/null || true
mysql -u root -e "GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${MIGRATION_USER}'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;"

echo "数据库 ${DB_NAME} 已创建。Flyway 使用 ${MIGRATION_USER} 执行迁移，应用使用 ${DB_USER}。"
echo "迁移完成后，Flyway callback 会为应用账号按表授予最小权限。"
echo "（core/src/main/resources/db/migration/V1__init_schema.sql）。"
