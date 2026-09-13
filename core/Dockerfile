# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY core/pom.xml core/pom.xml
RUN mvn -f core/pom.xml -DskipTests dependency:go-offline
COPY core core
RUN mvn -f core/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG BUILD_COMMIT=unknown
ENV BUILD_COMMIT=${BUILD_COMMIT}
LABEL org.opencontainers.image.revision=${BUILD_COMMIT}

RUN addgroup -S ycsopen && adduser -S -G ycsopen ycsopen
WORKDIR /app
COPY --from=build --chown=ycsopen:ycsopen /workspace/core/target/ycsopen-sms-core.jar /app/ycsopen-sms-core.jar

USER ycsopen
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/ycsopen-sms-core.jar"]
