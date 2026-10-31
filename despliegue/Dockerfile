# =============================================================================
#  La imagen de Visum para el despliegue en internet (ADR-0026).
#  Se construye desde la raiz del repositorio; docker-compose.yml ya lo hace asi.
# =============================================================================

# --- Compilacion ---
# Los tests no corren aca: los corre el CI antes. En esta maquina solo se arma el JAR.
FROM eclipse-temurin:21-jdk-noble AS construccion
WORKDIR /fuente
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY config config
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon --console=plain

# --- Ejecucion ---
# noble y no la etiqueta suelta: el paquete de GTK 2 cambia de nombre entre versiones de Ubuntu,
# igual que en el CI. El reconocedor LBPH carga el modulo de ventanas de OpenCV, enlazado contra
# GTK 2, aunque el servidor no tenga pantalla (ADR-0007, aprendizaje 9).
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgtk2.0-0t64 curl \
    && rm -rf /var/lib/apt/lists/*

# Sin root: si alguien llega a ejecutar algo adentro, no es dueno ni del contenedor.
RUN useradd --create-home --uid 10001 visum
WORKDIR /app
COPY --from=construccion /fuente/build/libs/*-SNAPSHOT.jar app.jar
USER visum

ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=America/Argentina/Ushuaia
EXPOSE 8080

# Salud sin correo ni nada externo: si esto falla, es la aplicacion o la base.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# MaxRAMPercentage en 50 y no mas: los modelos faciales viven en memoria nativa, fuera del heap,
# y con 400 docentes son mas de 500 MB. Darle casi todo al heap deja sin lugar a OpenCV.
# ExitOnOutOfMemoryError: un proceso sin memoria no se recupera bien, y compose lo reinicia.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
