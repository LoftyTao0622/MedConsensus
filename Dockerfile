FROM maven:3.9-eclipse-temurin-17 AS backend-builder
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser
COPY --from=backend-builder /workspace/target/*.jar /app/app.jar

USER appuser
EXPOSE 8086

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
