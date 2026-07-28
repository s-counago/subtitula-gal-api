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

# CDS needs the classpath as an explicit list of jars with preserved timestamps,
# which is exactly what the tools jarmode produces. A fat jar cannot be archived.
RUN java -Djarmode=tools -jar subtitula-api.jar extract --destination /app/application \
 && rm subtitula-api.jar

WORKDIR /app/application

# Training run: boots the real context, records every class the JVM loaded, and exits
# at refresh instead of serving. It must happen in this image and from this working
# directory — an archive is only valid for the JVM that wrote it and for the exact
# classpath it saw, and the build stage above is a different image (jdk vs jre).
#
# Nothing here touches the network. Flyway and schema validation are off because both
# would connect to PlanetScale, and the dialect is named so Hibernate does not open a
# connection to detect it. The two Google placeholders are the only ones in
# application.yml with no default, so the context cannot be built without them.
#
# A class missed during training is not a correctness problem: it simply loads the
# normal way at runtime.
RUN java \
      -XX:ArchiveClassesAtExit=/app/application.jsa \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -DGOOGLE_CLIENT_ID=cds-training \
      -DGOOGLE_CLIENT_SECRET=cds-training \
      -jar subtitula-api.jar

RUN chown -R spring:spring /app

USER spring:spring
EXPOSE 8080

# -Xshare:auto is the default: if the archive is ever stale or unusable the JVM falls
# back to ordinary class loading, so a mismatch costs start-up time, never correctness.
ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/application.jsa", "-jar", "subtitula-api.jar"]
