#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "  E-Commerce Microservices - Docker Setup"
echo "=========================================="
echo ""

# Check for Docker
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker is not installed or not in PATH."
    echo "        Please install Docker first: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check for Docker Compose
if ! docker compose version &> /dev/null; then
    echo "[ERROR] Docker Compose is not available."
    echo "        Please install Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

cd "$PROJECT_DIR"

echo "[1/3] Building Docker images..."
echo "       This may take a few minutes on first run..."
echo ""
docker compose build
echo ""

echo "[2/3] Starting all services..."
docker compose up -d
echo ""

echo "[3/3] Waiting for services to become healthy..."
echo ""

check_service() {
    local name=$1
    local url=$2
    local max_attempts=30
    local attempt=1

    echo -n "  Waiting for $name... "
    while [ $attempt -le $max_attempts ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            local status
            status=$(docker inspect --format='{{.State.Health.Status}}' "$(docker compose ps -q "$name")" 2>/dev/null || echo "responding")
            echo "READY ($status)"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    echo " TIMEOUT"
    echo "  [WARN] $name did not become healthy within $((max_attempts * 2)) seconds."
    return 1
}

check_service "eureka-server-service" "http://localhost:8761/actuator/health"
check_service "api-gateway-service" "http://localhost:8080/actuator/health"
check_service "user-service" "http://localhost:8081/actuator/health"
check_service "product-service" "http://localhost:8082/actuator/health"

echo ""
echo "=========================================="
echo "  All services started!"
echo "=========================================="
echo ""
echo "  Eureka Dashboard: http://localhost:8761"
echo "  API Gateway:      http://localhost:8080"
echo "  User Service:     http://localhost:8081"
echo "  Product Service:  http://localhost:8082"
echo ""
echo "  Demo credentials:"
echo "    Email:    demo@example.com"
echo "    Password: Demo1234"
echo ""
echo "  Useful commands:"
echo "    docker compose ps          - List service status"
echo "    docker compose logs -f     - Follow all logs"
echo "    docker compose logs -f <service> - Follow service logs"
echo "    docker compose down        - Stop all services"
echo "    docker compose down -v     - Stop and clean up volumes"
echo "=========================================="
