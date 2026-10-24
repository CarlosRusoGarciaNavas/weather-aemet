# --- Etapa de build: JDK 21 + Maven (vía mvnw) ---
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

# Primero solo lo que define las dependencias: esta capa se reutiliza mientras no cambie el pom.
COPY --chmod=755 mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

# Después el código. Los tests ya corren en CI.
COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && mv target/weather-backend-*.jar app.jar

# --- Etapa final: solo el JRE 21 y el jar ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

EXPOSE 8080

# wget es el de busybox, ya incluido en Alpine. 127.0.0.1 evita depender de cómo resuelva localhost (IPv4/IPv6).
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

# Forma exec: java es el PID 1 y recibe el SIGTERM de docker stop (Boot hace apagado ordenado).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
