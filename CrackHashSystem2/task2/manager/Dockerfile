FROM gradle:8.7.0-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN bash -lc "for i in 1 2 3 4 5; do \
  gradle :manager:bootJar --no-daemon && exit 0; \
  echo \"Gradle build failed (attempt $i/5), retrying...\"; \
  sleep $((i * 8)); \
done; \
exit 1"

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/manager/build/libs/manager-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
