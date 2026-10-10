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
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
