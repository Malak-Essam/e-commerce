FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY shared-library ../shared-library
COPY inventory-service ./inventory-service
RUN apk add --no-cache maven && mvn clean package -pl inventory-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/inventory-service/target/*.jar app.jar
RUN apk add --no-cache curl
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
