#!/usr/bin/env bash
# 初始化本地 MySQL 数据库（开发环境用）。
# 用法: ./init-db.sh [db_name] [db_user] [db_password]
set -euo pipefail
DB_NAME="${1:-ycsopen_sms}"
DB_USER="${2:-ycsopen}"
DB_PASS="${3:-ycsopen}"

mysql -u root -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -e "CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';"
mysql -u root -e "GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'%'; FLUSH PRIVILEGES;"

echo "数据库 ${DB_NAME} 已创建。Schema 由 Flyway 在应用启动时自动执行"
echo "（core/src/main/resources/db/migration/V1__init_schema.sql）。"
