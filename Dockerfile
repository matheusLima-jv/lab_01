# =============================================================================
# Imagem da Logistics API: build multi-stage
# =============================================================================
# Por que multi-stage?
#   O estágio "build" tem JDK completo, Maven e o código-fonte. O estágio final
#   leva só o JRE e o resultado do build. A imagem final fica menor (centenas de
#   MB a menos), sobe mais rápido e tem menos superfície de ataque: não há
#   compilador, código-fonte nem Maven dentro do container de produção.
#
# Por que os testes não rodam aqui (-DskipTests)?
#   Os testes de integração usam Testcontainers, que precisa de um Docker
#   disponível, coisa que não existe dentro de um "docker build". Os testes
#   rodam no CI (.github/workflows/ci.yml) antes da imagem ser gerada; a imagem
#   só é construída a partir de um commit que já passou por eles.
# =============================================================================

# ---------------------------------------------------------------------------
# Estágio 1: build
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Cache de camadas: primeiro só o que define as dependências. Enquanto o pom.xml
# não mudar, o Docker reaproveita a camada com as dependências baixadas e a
# próxima build só recompila o código. O "--mount=type=cache" (BuildKit) guarda o
# repositório Maven entre builds, mesmo quando a camada precisa ser refeita.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline -DskipTests

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Djacoco.skip=true

# Extrai o fat jar em camadas (dependências, loader, código da aplicação).
# Na imagem final, cada parte vira uma camada Docker separada: um deploy que só
# mudou o código reaproveita as camadas de dependências (que são a maior parte)
# já presentes no servidor, e só a camada pequena da aplicação é transferida.
RUN java -Djarmode=tools -jar target/logistics-api-*.jar extract --layers --launcher --destination extracted

# ---------------------------------------------------------------------------
# Estágio 2: runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Nunca rodar como root: se a aplicação for comprometida, o atacante fica com
# um usuário sem privilégios dentro do container.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Ordem das camadas: da que menos muda para a que mais muda (melhor cache).
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080

# Ajustes da JVM para containers:
#  - MaxRAMPercentage=75: o heap usa 75% da memória limite do CONTAINER (e não
#    da máquina). Os 25% restantes ficam para metaspace, threads e buffers.
#  - ExitOnOutOfMemoryError: em vez de continuar "meio viva" após um OOM, a JVM
#    encerra e o orquestrador reinicia o container.
#  - user.timezone=UTC: o horário do processo não depende do servidor.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"

# Healthcheck usando o readiness probe do Actuator (o wget do BusyBox já vem no Alpine).
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness > /dev/null || exit 1

# Forma "exec" (array JSON): o Java vira o PID 1 e recebe o SIGTERM direto, o que
# ativa o graceful shutdown. Na forma "shell" (string), o sinal iria para o sh.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
