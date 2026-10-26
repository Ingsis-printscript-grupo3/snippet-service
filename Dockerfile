# compila con el JDK completo. No hace falta token: este servicio no depende de la libreria de PrintScript
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# las dependencias primero y el codigo despues: si solo cambia el codigo, Docker reusa esta capa
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
# gradlew llega sin permiso de ejecucion desde Windows
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet

COPY src src
RUN ./gradlew bootJar --no-daemon --quiet

# la imagen final arranca solo con el JRE y el jar
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 snippet
USER snippet

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# Docker (y despues Swarm) sabe si el servicio esta sano, no solo si el proceso corre: el compose puede
# esperar con service_healthy y Swarm reemplaza el contenedor si deja de responder. curl ya viene en la
# imagen base. El start-period le da tiempo a Spring y a Flyway de arrancar
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# sin esto la JVM usa solo el 25% de la memoria que le da el contenedor
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
