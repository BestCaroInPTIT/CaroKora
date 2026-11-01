FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/caro-server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
