# 10. Execução e operação

> **Resumo:** três formas de rodar (tudo no Docker, IDE + infraestrutura no Docker, ou
> Testcontainers), configuração por variáveis de ambiente (12-factor), imagem Docker multi-stage
> não-root, health checks para orquestradores, métricas e logs estruturados com correlation ID.

## Formas de rodar

| Modo | Comando | Quando usar |
|---|---|---|
| Tudo no Docker | `docker compose up --build -d` | demonstração, avaliação de case técnico |
| IDE + infraestrutura | `docker compose up -d postgres mongo rabbitmq` e rodar `LogisticsApplication` com o perfil `local` | desenvolvimento do dia a dia (debug, hot reload) |
| Testcontainers | `./mvnw spring-boot:test-run` | experimentar sem configurar nada |

Credenciais de desenvolvimento: operador `operador@logistics.local` / `operador-local-123`;
RabbitMQ UI `logistics` / `logistics`.

## Configuração

Toda configuração está no `application.yml` (fonte única) e pode ser sobrescrita por variável
de ambiente, sem rebuild. É o fator III do [12-factor app](https://12factor.net/pt_br/config).

| Variável | Padrão | Descrição |
|---|---|---|
| `APP_SECURITY_JWT_SECRET` | **sem padrão** (obrigatória) | segredo HMAC em Base64, ≥ 32 bytes (`openssl rand -base64 32`) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/logistics` | JDBC do PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | `logistics` / `logistics` | credenciais |
| `DB_POOL_SIZE` | `10` | tamanho máximo do pool (HikariCP) |
| `MONGODB_URI` | `mongodb://localhost:27017/logistics` | conexão MongoDB |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | broker |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `logistics` / `logistics` | credenciais |
| `VIACEP_BASE_URL` | `https://viacep.com.br` | URL da ViaCEP |
| `CORS_ALLOWED_ORIGINS` | vazio | origens de navegador permitidas (vírgula) |
| `BOOTSTRAP_OPERATOR_ENABLED` / `_EMAIL` / `_PASSWORD` | `false` | cria um operador na subida |
| `API_DOCS_ENABLED` | `true` | liga/desliga Swagger e `/v3/api-docs` |
| `SERVER_PORT` | `8080` | porta HTTP |
| `SPRING_PROFILES_ACTIVE` | — | `local` para desenvolvimento |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | — | `ecs` = logs em JSON |

Qualquer chave do YAML vira variável de ambiente pela convenção do Spring (*relaxed binding*):
`app.outbox.batch-size` → `APP_OUTBOX_BATCH_SIZE`.

**Fail fast:** a configuração é validada na subida (`@Validated` nos records de propriedades).
Sem `APP_SECURITY_JWT_SECRET`, ou com um segredo curto, a aplicação **não sobe**, com mensagem
clara. É melhor do que subir com um padrão inseguro.

## Imagem Docker

`Dockerfile` multi-stage:

1. **build**: JDK 21 + Maven Wrapper; dependências em camada própria (cache); `package` sem testes
   (os testes rodam no CI, porque Testcontainers precisa de Docker); extração do jar em camadas.
2. **runtime**: JRE 21 Alpine, usuário `app` (não-root), camadas copiadas da que menos muda
   (dependências) para a que mais muda (código), `HEALTHCHECK` no readiness, `ENTRYPOINT` em forma
   exec (o Java é o PID 1 e recebe o `SIGTERM`, ativando o *graceful shutdown*).

```bash
docker build -t logistics-api:1.0.0 .
docker run --rm -p 8080:8080 -e APP_SECURITY_JWT_SECRET=$(openssl rand -base64 32) \
  -e DB_URL=... -e MONGODB_URI=... -e RABBITMQ_HOST=... logistics-api:1.0.0
```

Flags de JVM no `JAVA_TOOL_OPTIONS` da imagem: `MaxRAMPercentage=75` (heap proporcional ao
limite do container), `ExitOnOutOfMemoryError` (morre e é reiniciado em vez de ficar "zumbi") e
UTC.

## Docker Compose

- Healthchecks em todos os serviços + `depends_on: condition: service_healthy`: a API só sobe
  depois que banco, Mongo e broker estão prontos de verdade (não basta o container existir).
- Volumes nomeados: os dados sobrevivem a `down` (somem só com `down -v`).
- RabbitMQ com usuário próprio: o `guest` padrão só aceita conexões de localhost.
- Logs em JSON (ECS) na API.

## Observabilidade

### Saúde

| Endpoint | Acesso | Uso |
|---|---|---|
| `/actuator/health/liveness` | público | o processo está vivo? Se falhar, o orquestrador reinicia o container |
| `/actuator/health/readiness` | público | consegue atender? Se falhar, sai do balanceador (sem reiniciar) |
| `/actuator/health` | público (status); detalhes só para `OPERATOR` | db, mongo, rabbit, circuitBreakers, disco |

Separar liveness de readiness evita reinícios em cascata: se o PostgreSQL cair, a API fica "não
pronta" (sai do balanceador), mas não é reiniciada em loop, o que não resolveria nada.

### Métricas (`/actuator/metrics`, só operador)

| Métrica | O que mostra |
|---|---|
| `http.server.requests` | latência e volume por endpoint/status |
| `cache.gets` (`result=hit/miss`) | eficácia do cache de CEPs |
| `resilience4j.circuitbreaker.state` | estado do circuito da ViaCEP |
| `resilience4j.retry.calls` | retries feitos |
| `rabbitmq.*`, `hikaricp.*`, `jvm.*` | broker, pool de conexões, memória/GC |

Evolução natural: `micrometer-registry-prometheus` + Grafana, com o management numa porta
interna (`management.server.port`).

### Logs

- Toda linha tem o **correlation ID** (`[logistics-api,<id>]` no texto; campo `correlationId` no
  JSON). Ele vem do header `X-Correlation-Id` ou é gerado, volta na resposta e no corpo de erros.
- Política: 4xx não gera log de erro (é o sistema funcionando); 503 gera WARN; 500 gera ERROR com
  stack trace (que **nunca** vai para o cliente).
- Credenciais nunca são logadas (`toString` mascarado em senhas, hashes, tokens e segredos).

```bash
docker compose logs -f app | jq 'select(.correlationId == "abc-123")'
```

## Graceful shutdown

`server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 20s`: ao receber
`SIGTERM`, a API para de aceitar requisições, termina as em andamento e encerra os consumidores
do RabbitMQ (mensagens sem ack voltam para a fila e serão entregues de novo, por isso o consumidor
é idempotente).

## Troubleshooting

| Sintoma | Causa provável | Solução |
|---|---|---|
| A aplicação não sobe: `app.security.jwt.secret` | segredo ausente | defina `APP_SECURITY_JWT_SECRET` ou use o perfil `local` |
| `must have at least 256 bits` | segredo curto | `openssl rand -base64 32` |
| `ACCESS_REFUSED` no RabbitMQ | usuário `guest` vindo de outro host | use `logistics`/`logistics` (padrão do compose) |
| Testes `*IT` falham com "Could not find a valid Docker environment" | Docker não está rodando | inicie o Docker; os `*Test` rodam sem ele |
| `503 EXTERNAL_SERVICE_UNAVAILABLE` ao criar envio | ViaCEP inacessível (rede, proxy, TLS) | verifique a saída para `viacep.com.br`; em rede corporativa, configure `-Dhttps.proxyHost` |
| Rastreamento vazio logo após criar | consistência eventual (até ~1 s) | aguarde; se persistir, olhe `outbox_messages.last_error` e a DLQ |
| `Schema-validation: wrong column type` | entidade JPA e migração divergentes | crie uma nova migração Flyway; nunca edite uma já aplicada |

## CI (GitHub Actions)

`.github/workflows/ci.yml`, em push na `main` e em pull requests: `./mvnw verify` (todos os testes
+ Testcontainers + gate de cobertura), publicação do relatório JaCoCo e build da imagem Docker com
cache. Permissões mínimas (`contents: read`) e cancelamento de execuções concorrentes do mesmo
branch.
