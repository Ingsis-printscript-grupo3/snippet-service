# compila con el JDK completo. No hace falta token: este servicio no depende de la libreria de PrintScript
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# el agente de New Relic. La version esta fija y el checksum va con esa version: si el zip
# cambiara, el build falla en vez de meter algo distinto en la imagen
ARG NEW_RELIC_AGENT_VERSION=9.5.0
ADD --checksum=sha256:5f32e34216b5f23b41973d098e61ae49ee4a05dd79394c0235b26e9203b57317 https://download.newrelic.com/newrelic/java-agent/newrelic-agent/${NEW_RELIC_AGENT_VERSION}/newrelic-java.zip /tmp/newrelic.zip
# jar xf: ya viene con el JDK, evita instalar unzip
RUN cd /tmp && jar xf newrelic.zip

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

# solo el jar y su config: el zip trae ademas javadocs y sources que no van a produccion
COPY --from=build /tmp/newrelic/newrelic.jar /tmp/newrelic/newrelic.yml /app/newrelic/
# el agente loguea a stdout y no a un archivo: el usuario no root no puede escribir en /app
ENV NEW_RELIC_LOG_FILE_NAME=STDOUT

EXPOSE 8080

# Docker (y despues Swarm) sabe si el servicio esta sano, no solo si el proceso corre: el compose puede
# esperar con service_healthy y Swarm reemplaza el contenedor si deja de responder. curl ya viene en la
# imagen base. El start-period le da tiempo a Spring y a Flyway de arrancar
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# sin esto la JVM usa solo el 25% de la memoria del contenedor. 60 y no 75: el agente, el
# metaspace y los stacks viven fuera del heap, dentro del mismo limite de 512M
# -javaagent: el agente entra a la JVM antes que Spring. Su config son las variables NEW_RELIC_*
ENTRYPOINT ["java", "-javaagent:/app/newrelic/newrelic.jar", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
