# Docker Setup for Local Development

This directory contains Docker configuration for running the entire e-commerce
microservices platform with a single command.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) 24.0+
- [Docker Compose](https://docs.docker.com/compose/install/) (included with
  Docker Desktop)
- `curl` (for the startup script health checks)

## Quick Start

```bash
# From the project root:
./scripts/start-services.sh
```

Or manually:

```bash
docker compose build
docker compose up -d
```

## Accessing Services

| Service | URL | Description |
|---|---|---|
| Eureka Dashboard | http://localhost:8761 | Service registry & discovery |
| API Gateway | http://localhost:8080 | Entry point for all API calls |
| User Service | http://localhost:8081 | User management & auth |
| Product Service | http://localhost:8082 | Product catalog |
| User H2 Console | http://localhost:8081/h2-console | User DB console |
| Product H2 Console | http://localhost:8082/h2-console | Product DB console |

### Demo Credentials

- **Email:** `demo@example.com`
- **Password:** `Demo1234`

### API Endpoints (via Gateway)

```bash
# Login
curl -X POST http://localhost:8080/api/users/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"Demo1234"}'

# Get products
curl http://localhost:8080/api/products
```

## How It Works

### Architecture

```
                    ┌──────────────────────┐
                    │  eureka-server-service │
                    │     (port 8761)        │
                    └───────────┬────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│api-gateway-   │     │  user-service │     │product-service│
│   service     │     │  (port 8081)  │     │ (port 8082)   │
│  (port 8080)  │     │               │     │               │
│               │     │  DB: H2 mem   │     │  DB: H2 mem   │
│ Routes:       │     │               │     │               │
│ /api/users/** │────>│ Auth: JWT     │     │ Products: CRUD│
│ /api/products││────>│               │────>│               │
└───────────────┘     └───────────────┘     └───────────────┘
```

### Container Networking

All services are connected via a custom bridge network (`ecommerce-network`)
and communicate using Docker service names (e.g., `http://user-service:8081`).

### Startup Order

1. **Eureka Server** starts first — health check waits for `/actuator/health`
2. **API Gateway, User Service, Product Service** start in parallel once
   Eureka reports healthy
3. Each service registers with Eureka upon startup

### Dockerfiles

Each service has a multi-stage Dockerfile:

| Stage | Base Image | Purpose |
|---|---|---|
| `build` | `maven:3.9-eclipse-temurin-21-alpine` | Compiles source code |
| `runtime` | `eclipse-temurin:21-jre-alpine` | Runs the JAR (~180MB final image) |

## Useful Commands

```bash
# View all running containers
docker compose ps

# Follow logs from all services
docker compose logs -f

# Follow logs from a specific service
docker compose logs -f user-service

# Rebuild a specific service
docker compose build user-service

# Restart a specific service
docker compose restart user-service

# Stop all services
docker compose down

# Stop all services and remove volumes (resets databases)
docker compose down -v

# Run a command in a running container
docker compose exec user-service sh

# View resource usage
docker stats
```

## Troubleshooting

### Services aren't registering with Eureka

1. Check Eureka is healthy:
   ```bash
   curl http://localhost:8761/actuator/health
   ```
2. Check individual service logs:
   ```bash
   docker compose logs user-service | grep -i eureka
   ```
3. Verify environment variables:
   ```bash
   docker compose exec user-service env | grep EUREKA
   ```

### Connection refused errors

1. Ensure all services are on the same network:
   ```bash
   docker compose ps
   ```
2. Check DNS resolution inside containers:
   ```bash
   docker compose exec user-service sh -c "curl -sf http://eureka-server-service:8761/actuator/health"
   ```

### Container exits immediately

Check logs for the specific service:
```bash
docker compose logs product-service
```

Common causes:
- Port already in use on host — change the host port mapping
- Out of memory — increase Docker memory limit

### Rebuilding after code changes

```bash
docker compose build
docker compose up -d
```

To force a complete rebuild without cache:
```bash
docker compose build --no-cache
docker compose up -d
```

### Performance

First build takes 2-5 minutes depending on internet speed (downloading Maven
dependencies). Subsequent builds are faster thanks to Docker layer caching.

## Environment Variables

Referenced in `application.yml` files via placeholders:

| Variable | Default | Description |
|---|---|---|
| `EUREKA_URI` | `http://localhost:8761/eureka/` | Eureka server URL |
| `INSTANCE_HOSTNAME` | `localhost` | Hostname registered with Eureka |
