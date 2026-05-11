FROM gradle:9-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
# Меняется на каждый коммит в CI — иначе BuildKit/GHA cache может отдать старый слой с JAR без актуального Kotlin.
ARG CI_GIT_SHA=local
RUN echo "CI_GIT_SHA=${CI_GIT_SHA}" && ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
