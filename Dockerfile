FROM node:22-bookworm-slim AS frontend-build
WORKDIR /workspace/front

COPY front/package*.json ./
RUN npm ci

COPY front/ ./
RUN npm run build


FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace/receiver

COPY receiver/pom.xml ./

COPY receiver/src ./src
RUN mvn -B -DskipTests package


FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Shanghai \
    SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    PLC_FRONTEND_DIR=/app/front/dist \
    PLC_UPLOAD_ROOT=/app/uploads \
    SPRING_DATASOURCE_URL=jdbc:sqlite:/app/data/plc_detection.db \
    LOGGING_FILE_NAME=/app/logs/receiver.log

COPY --from=frontend-build /workspace/front/dist /app/front/dist
COPY --from=backend-build /workspace/receiver/target/*.jar /app/app.jar

RUN mkdir -p /app/data /app/uploads /app/logs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
