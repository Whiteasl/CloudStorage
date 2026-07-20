# 构建前端

FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build:docker

# 构建后端
FROM maven:3-eclipse-temurin-17 AS backend
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src/ src/
COPY --from=frontend /app/frontend/dist src/main/resources/static
RUN ./mvnw package -DskipTests

# 运行
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 8080
RUN apk add --no-cache openssl
COPY ./docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh
ENTRYPOINT [ "/app/docker-entrypoint.sh" ]