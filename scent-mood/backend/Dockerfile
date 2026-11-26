FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
COPY database/migrations database/migrations
RUN mvn -B -f backend/pom.xml package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S scentmood && adduser -S -G scentmood scentmood \
    && mkdir -p /app/data && chown scentmood:scentmood /app/data
WORKDIR /app
COPY --from=build /workspace/backend/target/scent-mood-api-1.0.0.jar app.jar
USER scentmood
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
