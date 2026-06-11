#!/bin/bash
# Eureka Server startup script
# Starts the Eureka Server on port 8761

APP_NAME="eureka-server-service"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "============================================"
echo "  Starting $APP_NAME"
echo "============================================"

cd "$PROJECT_DIR" || exit 1

# Check if Maven wrapper exists
if [ -f "./mvnw" ]; then
    MVN_CMD="./mvnw"
else
    MVN_CMD="mvn"
fi

# Build and run
echo "Building $APP_NAME..."
$MVN_CMD clean package -DskipTests -q

echo "Starting Eureka Server on port 8761..."
$MVN_CMD spring-boot:run

