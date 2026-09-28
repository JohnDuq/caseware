FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system caseware \
    && useradd --system --gid caseware --home-dir /app --shell /usr/sbin/nologin caseware \
    && mkdir -p /app/data \
    && chown -R caseware:caseware /app

COPY --from=build --chown=caseware:caseware /workspace/target/interview-*.jar /app/app.jar

USER caseware

EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
