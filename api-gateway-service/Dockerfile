FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY shared-library shared-library
COPY eureka-server-service eureka-server-service
COPY api-gateway-service api-gateway-service
COPY user-service user-service
COPY product-service product-service

RUN mvn clean package -pl api-gateway-service -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

COPY --from=build /app/api-gateway-service/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
