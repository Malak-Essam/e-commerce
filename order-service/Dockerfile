FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY shared-library shared-library/
COPY order-service order-service/
RUN mvn -pl order-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/order-service/target/*.jar app.jar
EXPOSE 8085
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=30s \
  CMD curl -f http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
