# Eureka Server Service

Service registry for dynamic microservice discovery using Netflix Eureka.

## Overview

Eureka Server acts as a service registry where all microservices register themselves. Other services can discover and communicate with each other dynamically without hardcoded URLs.

## Prerequisites

- Java 21
- Maven 3.8+
- Docker (optional, for containerized deployment)

## Configuration

| Property | Value |
|----------|-------|
| Port | 8761 |
| Dashboard | http://localhost:8761 |
| REST API | http://localhost:8761/eureka/apps |

### Key Settings (application.yml)

- **Self-registration**: Disabled (`register-with-eureka: false`, `fetch-registry: false`)
- **Self-preservation**: Disabled for development
- **Eviction interval**: 5 seconds

## Running Locally

### Using Maven

```bash
cd eureka-server-service
mvn clean package -DskipTests
mvn spring-boot:run
```

### Using the startup script

```bash
./scripts/start.sh
```

### Using Docker

```bash
# Build the image
docker build -t eureka-server-service .

# Run the container
docker run -p 8761:8761 eureka-server-service
```

## Verification

Once the server is running:

1. **Dashboard**: Open http://localhost:8761 in your browser
2. **REST API**: Visit http://localhost:8761/eureka/apps to see registered services
3. **Health**: Check http://localhost:8761/actuator/health (if actuator is enabled)

The dashboard shows:
- **Instances currently registered**: Lists all registered microservices
- **General Info**: Server uptime and environment information

## Adding Services

Other microservices register with Eureka by adding the `spring-cloud-starter-netflix-eureka-client` dependency and this configuration:

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    fetch-registry: true
    register-with-eureka: true
```

## Troubleshooting

- **Port already in use**: Ensure port 8761 is free, or change the port in `application.yml`
- **Services not registering**: Check that the `defaultZone` URL is correct and the Eureka Server is running
- **High log verbosity**: Adjust `logging.level.com.netflix.eureka` in `application.yml`
