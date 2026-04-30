FROM gradle:8.7.0-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle :manager:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/manager/build/libs/manager-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
