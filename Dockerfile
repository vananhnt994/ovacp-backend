# ==============================================================================
#  OCVAP Backend – Multi-Stage Dockerfile
#
#  Lokal (docker compose):  einzelne Images via "target: eureka" etc.
#  Cloud Run:               ein Image mit allen Services via "target: cloud-run"
#
#  Build lokal:    docker compose build
#  Build Cloud:    docker build --target cloud-run -t ocvap-backend .
# ==============================================================================

# ── Stage 1: Maven-Build aller Module ────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# 1a) Nur POMs kopieren -> Docker-Layer-Cache fuer Dependencies
COPY pom.xml ./
COPY eureka/pom.xml            eureka/
COPY gateway/pom.xml           gateway/
COPY usermanamgement/pom.xml   usermanamgement/
COPY file-management/pom.xml   file-management/
COPY ai-analysis-service/pom.xml  ai-analysis-service/
COPY chart-data-service/pom.xml   chart-data-service/

# 1b) Dependencies vorab aufloesen (gecached solange POMs sich nicht aendern)
RUN mvn dependency:go-offline -B -q || true

# 1c) Quellcode aller Module kopieren
COPY eureka/src            eureka/src
COPY gateway/src           gateway/src
COPY usermanamgement/src   usermanamgement/src
COPY file-management/src   file-management/src
COPY ai-analysis-service/src  ai-analysis-service/src
COPY chart-data-service/src   chart-data-service/src

# 1d) Alles bauen
RUN mvn package -DskipTests -B -q


# ── Einzelne Stages (fuer docker compose / lokale Entwicklung) ───────────────

FROM eclipse-temurin:21-jre-alpine AS eureka
WORKDIR /app
COPY --from=build /workspace/eureka/target/eureka-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS gateway
WORKDIR /app
COPY --from=build /workspace/gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS usermanagement
WORKDIR /app
COPY --from=build /workspace/usermanamgement/target/usermanagement-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS file-management
WORKDIR /app
RUN mkdir -p /app/uploads
COPY --from=build /workspace/file-management/target/file-management-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS ai-analysis
WORKDIR /app
COPY --from=build /workspace/ai-analysis-service/target/ai-analysis-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS chart-data
WORKDIR /app
COPY --from=build /workspace/chart-data-service/target/chart-data-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]


# ── Cloud Run: ALLE Services in einem Container ─────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS cloud-run
WORKDIR /app

RUN mkdir -p /app/uploads

COPY --from=build /workspace/eureka/target/eureka-server-0.0.1-SNAPSHOT.jar          eureka.jar
COPY --from=build /workspace/gateway/target/api-gateway-0.0.1-SNAPSHOT.jar            gateway.jar
COPY --from=build /workspace/usermanamgement/target/usermanagement-0.0.1-SNAPSHOT.jar usermanagement.jar
COPY --from=build /workspace/file-management/target/file-management-0.0.1-SNAPSHOT.jar file-management.jar
COPY --from=build /workspace/ai-analysis-service/target/ai-analysis-service-0.0.1-SNAPSHOT.jar ai-analysis.jar
COPY --from=build /workspace/chart-data-service/target/chart-data-service-0.0.1-SNAPSHOT.jar   chart-data.jar

COPY startup.sh /app/startup.sh
RUN sed -i 's/\r$//' /app/startup.sh && chmod +x /app/startup.sh

EXPOSE 8080

CMD ["/app/startup.sh"]
