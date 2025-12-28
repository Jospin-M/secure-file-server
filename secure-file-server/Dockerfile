# ---------- Build stage ----------
    FROM maven:3.9-eclipse-temurin-21 AS build

    WORKDIR /build

    # copy pom.xml first for layer cachine
    COPY pom.xml .
    RUN mvn dependency:go-offline

    # copy source and build JAR
    COPY src ./src
    RUN mvn package -DskipTests


    # ---------- Runtime stage ----------
    FROM eclipse-temurin:21-jre-jammy

    WORKDIR /app

    # copy built JAR explicitly 
    COPY --from=build /build/target/secure-file-server-1.0-SNAPSHOT.jar /app/server.jar

    # copy scripts
    COPY scripts/run-server.sh /app/run-server.sh
    COPY scripts/generate-cert.sh /app/generate-cert.sh

    # copy runtime directories
    COPY certs /app/certs
    COPY config /app/config
    COPY uploads /app/uploads
    COPY .env /app/.env

    # ensure scripts are executable
    RUN chmod +x /app/*.sh

    # ensure uploads are persistent
    VOLUME /app/uploads

    # HTTPS port
    EXPOSE 8443

    # start server
    ENTRYPOINT ["/app/run-server.sh"]