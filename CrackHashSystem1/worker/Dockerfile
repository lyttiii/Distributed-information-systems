FROM gradle:8.7.0-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle :worker:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/worker/build/libs/worker-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
