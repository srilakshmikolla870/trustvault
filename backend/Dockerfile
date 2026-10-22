FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 trustvault \
    && mkdir -p /var/lib/trustvault/files \
    && chown -R trustvault:trustvault /var/lib/trustvault
COPY --from=build /workspace/target/trustvault-api-*.jar /app/app.jar
USER trustvault
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
