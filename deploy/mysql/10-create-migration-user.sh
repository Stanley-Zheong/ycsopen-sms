# shellcheck shell=bash
# This file is sourced by the official MySQL image during first-volume setup.
# Compose validates the identifiers and local-only passwords before this point.
case "${MYSQL_DATABASE:-}" in
  ''|*[!A-Za-z0-9_]*) echo >&2 'MYSQL_DATABASE must contain only letters, digits, and underscore'; exit 1 ;;
esac
case "${DB_MIGRATION_USERNAME:-}" in
  ''|*[!A-Za-z0-9_.-]*) echo >&2 'DB_MIGRATION_USERNAME contains unsupported characters'; exit 1 ;;
esac
case "${DB_MIGRATION_PASSWORD:-}" in
  ''|*[!A-Za-z0-9_.@%+=!-]*) echo >&2 'DB_MIGRATION_PASSWORD contains unsupported characters'; exit 1 ;;
esac

docker_process_sql <<-EOSQL
CREATE USER IF NOT EXISTS '${DB_MIGRATION_USERNAME}'@'%' IDENTIFIED BY '${DB_MIGRATION_PASSWORD}';
ALTER USER '${DB_MIGRATION_USERNAME}'@'%' IDENTIFIED BY '${DB_MIGRATION_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${DB_MIGRATION_USERNAME}'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
EOSQL
