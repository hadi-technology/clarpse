FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY . .
RUN mvn clean package assembly:single \
    && JAR_PATH=$(ls /app/target/clarpse-*.jar | head -n 1) \
    && cp "$JAR_PATH" /app/clarpse.jar

FROM eclipse-temurin:17-jre

RUN apt-get update \
    && apt-get install -y curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && npm install -g typescript \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/clarpse.jar /app/clarpse.jar

EXPOSE 8080
ENV CLARPSE_PORT=8080
ENTRYPOINT ["java", "-cp", "/app/clarpse.jar", "com.hadi.clarpse.server.ClarpseServer"]
