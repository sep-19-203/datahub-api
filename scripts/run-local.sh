#!/bin/sh
# Chạy hddt-data-api-service bằng java -jar ở máy local (không OpenShift).
# Cấu hình/credential đọc từ file NGOÀI repo (mặc định ~/.config/hddt/data-api-local.env, quyền 600) — mẫu:
#   config/data-api-local.env.example
# Yêu cầu: JAVA_HOME trỏ JDK/JRE 8 (hoặc mới hơn); đã build: mvn -B package -DskipTests
set -eu
ENV_FILE="${DATA_API_ENV:-$HOME/.config/hddt/data-api-local.env}"
[ -f "$ENV_FILE" ] || { echo "Thiếu $ENV_FILE (xem config/data-api-local.env.example)"; exit 1; }
# shellcheck disable=SC1090
. "$ENV_FILE"
DIR=$(cd "$(dirname "$0")/.." && pwd)
JAR="$DIR/target/hddt-data-api-service.jar"
[ -f "$JAR" ] || { echo "Chưa có $JAR — chạy: mvn -B package -DskipTests"; exit 1; }
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVA" -XX:+UseG1GC -Xmx2g -Duser.timezone=UTC -jar "$JAR" --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-local}"
