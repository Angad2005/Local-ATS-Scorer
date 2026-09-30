FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
COPY --from=frontend-build /frontend/dist /frontend/dist
RUN mvn -q -DskipTests -Dskip.frontend=true package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/ats-scorer-0.1.0.jar ./app.jar
RUN mkdir -p /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]