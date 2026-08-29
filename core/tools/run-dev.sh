#!/usr/bin/env bash
# 本地起服务：先跑 init-db.sh，再用 dev profile 启动 Spring Boot。
set -euo pipefail
cd "$(dirname "$0")/.."
./tools/init-db.sh
mvn spring-boot:run -Dspring-boot.run.profiles=dev
