FROM node:22-slim AS frontend
WORKDIR /app
COPY frontend/package*.json frontend/
RUN cd frontend && npm ci
COPY frontend/ frontend/
RUN mkdir -p backend/src/main/resources/static && cd frontend && npm run build

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
COPY --from=frontend /app/backend/src/main/resources/static backend/src/main/resources/static
RUN ./gradlew :backend:shadowJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/build/libs/backend-*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
