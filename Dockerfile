FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /workspace/target/subtitula-api-*.jar /app/subtitula-api.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/subtitula-api.jar"]
