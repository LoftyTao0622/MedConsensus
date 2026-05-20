FROM m.daocloud.io/docker.io/library/node:20-alpine AS frontend-builder
WORKDIR /workspace

COPY frontend/package*.json ./frontend/
RUN cd frontend && npm ci

COPY frontend ./frontend
RUN mkdir -p src/main/resources/static && cd frontend && npm run build

FROM m.daocloud.io/docker.io/library/maven:3.9-eclipse-temurin-17 AS backend-builder
WORKDIR /workspace

COPY docker/deploy/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B -s /root/.m2/settings.xml -DskipTests dependency:go-offline

COPY src ./src
COPY --from=frontend-builder /workspace/src/main/resources/static ./src/main/resources/static
RUN mvn -B -s /root/.m2/settings.xml -DskipTests package

FROM m.daocloud.io/docker.io/library/eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser
COPY --from=backend-builder /workspace/target/*.jar /app/app.jar

USER appuser
EXPOSE 8086

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
