# 12. Guia de estudo

Este projeto foi construído para ser **lido**. Este guia propõe uma ordem de leitura do código em
fases, do centro para as bordas da arquitetura. Cada fase traz os conceitos, os arquivos, as
perguntas que você deve conseguir responder e exercícios para fixar.

> Dica: o histórico do Git conta a mesma história. Cada commit é uma fase
> (`git log --oneline --reverse`), e `git show <commit> --stat` mostra o que entrou em cada uma.

## Fase 1: Domínio (o coração, Java puro)

**Conceitos:** Value Object, entidade, agregado, invariante, máquina de estados, evento de
domínio, domain service, modelo rico x anêmico.

**Leia, nesta ordem:**
1. `shipping/domain/PostalCode.java`, `Weight.java`, `Money.java`: Value Objects
2. `shipping/domain/Address.java`, `BrazilianState.java`
3. `shipping/domain/ShipmentStatus.java`: máquina de estados
4. `shipping/domain/ShipmentEvent.java`: interface selada com records
5. `shared/domain/AggregateRoot.java` e `shipping/domain/Shipment.java`: o agregado
6. `shipping/domain/freight/*`: domain service e tabela configurável
7. Testes: `ShipmentTest`, `ShipmentStatusTest`, `FreightCalculatorTest`

**Você deve conseguir responder:**
- Por que `Shipment` não tem `setStatus`?
- Por que `Money` normaliza a escala no construtor? O que aconteceria com `equals` sem isso?
- Por que `Weight` usa gramas inteiros?
- Qual a diferença entre `Shipment.create` e `Shipment.restore`?
- Por que o agregado registra eventos em vez de publicá-los?

**Exercícios:**
1. Adicione o status `RETURNED` (devolvido), permitido a partir de `IN_TRANSIT`. Rode
   `./mvnw test` e observe quantos `switch` o compilador obriga você a atualizar. Esse é o
   benefício dos tipos selados e enums exaustivos.
2. Crie a regra "envios acima de 20 kg pagam 10% a mais de frete". Onde ela mora? (Dica:
   `FreightCalculator`, com um teste parametrizado.)

## Fase 2: Casos de uso (orquestração)

**Conceitos:** portas de entrada e saída, Command/Query/View, application service, fronteira de
transação, autorização por dono, defesa em profundidade, fakes.

**Leia:**
1. `shipping/application/port/in/*`: o que o sistema faz
2. `shipping/application/port/out/*`: do que o núcleo precisa
3. `shipping/application/service/CreateShipmentService.java`: a ordem dos passos importa
4. `shipping/application/service/ChangeShipmentStatusService.java`: `switch` com record patterns
5. `shipping/application/service/ShipmentAccess.java`: regra anti-IDOR centralizada
6. `identity/application/service/AuthenticateUserService.java`: mitigação de timing attack
7. Testes: `ShippingUseCasesTest`, `IdentityUseCasesTest` e os fakes em `*/application/fake`

**Você deve conseguir responder:**
- Por que a ViaCEP é chamada **antes** de abrir a transação? O que aconteceria com o pool de
  conexões se fosse o contrário?
- Por que existe a porta `TransactionRunner` em vez de `@Transactional`?
- Por que um cliente recebe 404 (e não 403) ao acessar o envio de outra pessoa?
- Por que os testes usam fakes aqui e mocks nos controllers?

**Exercícios:**
1. Crie o caso de uso "operador lista envios por CEP de destino": porta de entrada, critério na
   porta de saída, serviço, bean em `ShippingConfig` e teste com fake.
2. Faça o `CreateShipmentService` consultar os dois CEPs em paralelo com virtual threads
   (`Executors.newVirtualThreadPerTaskExecutor()`). O teste de fronteira de transação continua
   passando?

## Fase 3: Adaptadores de saída (infraestrutura)

**Conceitos:** Flyway, JPA x domínio, mapeamento, Specifications, bloqueio otimista,
`Persistable`, MongoDB, outbox, publisher confirms, `FOR UPDATE SKIP LOCKED`, RestClient,
timeout, retry, circuit breaker, cache, JWT, BCrypt.

**Leia:**
1. `src/main/resources/db/migration/*.sql`: cada comentário explica uma decisão de schema
2. `shipping/adapter/out/persistence/*`: entidade, mapper, Specifications, adaptador
3. `shipping/adapter/out/tracking/*`: documento e idempotência no MongoDB
4. `shared/infrastructure/outbox/*`: o outbox completo
5. `shipping/adapter/out/messaging/OutboxShipmentEventPublisher.java` e
   `shipping/adapter/messaging/ShipmentEventMessage.java`
6. `shipping/adapter/out/viacep/*` e `shipping/config/ViaCepClientConfig.java`
7. `identity/adapter/out/security/*` e `shared/infrastructure/security/JwtConfig.java`
8. Testes `*IT` e `ViaCepPostalCodeLookupTest`

**Você deve conseguir responder:**
- O que é o problema da escrita dupla e como o outbox o resolve? Por que ainda pode haver
  duplicatas?
- Para que serve `SKIP LOCKED`?
- Por que `save` no Mongo esconderia duplicatas e `insert` não?
- Em que ordem estão retry e circuit breaker, e por quê?
- Por que o segredo do JWT não tem valor padrão?

**Exercícios:**
1. Troque o Caffeine por Redis no cache de CEP. Quantas classes do núcleo você precisou alterar?
   (Resposta esperada: nenhuma.)
2. Escreva um segundo adaptador para `PostalCodeLookup` usando a BrasilAPI
   (`https://brasilapi.com.br/api/cep/v1/{cep}`) e troque o bean. Os testes de caso de uso mudam?
3. Adicione uma migração `V4__` com uma coluna `estimated_delivery_date`. O que acontece se a
   entidade JPA não for atualizada? (Dica: `ddl-auto: validate`.)

## Fase 4: Adaptadores de entrada e segurança

**Conceitos:** controller fino, DTO de borda, Bean Validation, RFC 9457, `@RestControllerAdvice`,
Spring Security Resource Server, `SecurityFilterChain`, `@PreAuthorize`, argument resolver,
CORS, CSRF, correlation ID, listener idempotente, DLQ, `ApplicationRunner`.

**Leia:**
1. `shipping/adapter/in/web/ShipmentController.java`
2. `shared/infrastructure/web/GlobalExceptionHandler.java` e `ProblemDetailFactory.java`
3. `shared/infrastructure/security/SecurityConfig.java` e os dois handlers `ProblemDetail*`
4. `shared/infrastructure/security/RequesterArgumentResolver.java`
5. `shared/infrastructure/web/CorrelationIdFilter.java`
6. `shipping/adapter/in/messaging/TrackingProjectionListener.java` e
   `shipping/config/ShippingMessagingConfig.java`
7. `shared/infrastructure/openapi/OpenApiConfig.java`: por que o contrato gerado precisa de ajuste
8. Testes: `ShipmentControllerTest`, `IdentityControllersTest`
9. Pratique com `docs/api/requests.http`: rode a seção 5 (erros) e compare cada resposta com o
   catálogo de códigos da [API REST](04-api-rest.md)

**Você deve conseguir responder:**
- Por que o DTO de cadastro não tem o campo `role`? Que ataque isso evita?
- Por que CSRF pode ser desligado aqui?
- Por que `AccessDeniedException` precisa de um handler próprio quando existe um
  `@ExceptionHandler(Exception.class)`?
- O que acontece com uma mensagem com JSON inválido na fila de rastreamento?

**Exercícios:**
1. Adicione suporte a `ETag`/`If-None-Match` no `GET /shipments/{id}` (304 Not Modified).
2. Adicione um header `Idempotency-Key` no `POST /shipments`: a mesma chave repetida devolve o
   mesmo envio em vez de criar outro.
3. Traduza o `detail` dos erros gerados pelo próprio Spring MVC (rota inexistente, 405, 415), que
   hoje sai em inglês. Dica: crie `src/main/resources/messages.properties` com chaves
   `problemDetail.<nome completo da exceção>` (por exemplo,
   `problemDetail.org.springframework.web.HttpRequestMethodNotSupportedException`); o
   `ResponseEntityExceptionHandler` consulta o `MessageSource` antes de usar o texto padrão.
   Confirme com a requisição 5.17 do `requests.http` e com um teste.

## Fase 5: Testes, arquitetura executável e entrega

**Leia:** `ArchitectureTest`, `AbstractIntegrationTest`, `ShipmentJourneyIT`, `MessagingIT`,
`OpenApiContractIT`, `pom.xml` (Surefire/Failsafe/JaCoCo), `Dockerfile`, `docker-compose.yml`,
`.github/workflows/ci.yml`.

**Exercícios:**
1. Quebre a arquitetura de propósito (um `import org.springframework...` no domínio) e veja o
   `ArchitectureTest` falhar. Leia a mensagem.
2. Escreva uma regra ArchUnit: "classes em `adapter.out.persistence` não podem ser usadas fora do
   próprio pacote, exceto pela configuração".
3. Rode `docker compose up --build`, crie um envio pelo Swagger e acompanhe no painel do RabbitMQ
   a mensagem passando pela fila.

## Reaproveitando como base para cases técnicos

A estrutura se aplica a qualquer domínio. Para um case novo ("transferências entre carteiras",
"agendamento de consultas", "encurtador de URL"...):

1. **Comece pelo domínio** (fase 1): glossário, agregado com regras, Value Objects, máquina de
   estados se houver, testes de tabela. É o que mais impressiona quem avalia.
2. **Uma porta de entrada por caso de uso**, serviços sem Spring, beans no `config`.
3. **Adaptadores só para o que o case pede.** Não precisa de MongoDB? Não crie. A arquitetura
   não exige todas as tecnologias, só deixa espaço para elas.
4. **Copie a infraestrutura transversal** quase intacta: `shared/infrastructure/web` (Problem
   Details, correlation ID), `security`, `ClockConfig`, `SpringTransactionRunner`,
   `ArchitectureTest`, `pom.xml`, `Dockerfile`, `docker-compose.yml`, CI.
5. **Documente decisões** com ADRs curtos e um README com "como rodar em um comando".

### Checklist de entrega de um case

- [ ] Roda com um comando (`docker compose up`)
- [ ] README: o que é, como rodar, como testar, decisões e o que ficou de fora
- [ ] Regras de negócio no domínio, com testes unitários legíveis
- [ ] Erros padronizados (RFC 9457) e status HTTP corretos
- [ ] Validação na borda **e** no domínio
- [ ] Testes de integração contra infraestrutura real (Testcontainers)
- [ ] Segurança: autenticação, autorização por dono, sem dados sensíveis em logs/respostas
- [ ] Configuração externa, sem segredo no código
- [ ] Concorrência considerada (bloqueio otimista, idempotência)
- [ ] Integrações com timeout e tratamento de falha
- [ ] CI rodando os testes

### Perguntas que avaliadores costumam fazer (e onde está a resposta)

| Pergunta | Onde |
|---|---|
| "E se o RabbitMQ cair depois de salvar no banco?" | [Mensageria](07-mensageria.md) (outbox) |
| "E se a mesma mensagem chegar duas vezes?" | [Mensageria](07-mensageria.md) (idempotência) |
| "E se dois usuários alterarem ao mesmo tempo?" | [Persistência](06-persistencia.md) (bloqueio otimista) |
| "E se a API externa ficar lenta?" | [Integração externa](08-integracao-externa.md) (timeouts, circuit breaker) |
| "Por que não `@Transactional`?" | [ADR 0003](adr/0003-casos-de-uso-sem-framework.md) |
| "Como escalaria horizontalmente?" | stateless + `SKIP LOCKED` + consumidores concorrentes |
| "Por que dois bancos?" | [ADR 0005](adr/0005-postgresql-e-mongodb.md) |
| "Como garante a arquitetura ao longo do tempo?" | `ArchitectureTest` |

## Evoluções sugeridas

Ficaram de fora para manter o projeto estudável. Cada uma é um bom exercício:

| Evolução | Por que importa | Por onde começar |
|---|---|---|
| Refresh token + revogação | tokens de acesso curtos sem obrigar novo login | nova porta `RefreshTokenStore` (PostgreSQL ou Redis) |
| Identity Provider externo (Keycloak) | padrão corporativo, RS256/JWKS | `spring.security.oauth2.resourceserver.jwt.issuer-uri` |
| Rate limiting | brute force no login, abuso | Bucket4j ou API Gateway |
| `ETag` / `If-Match` | concorrência otimista entre requisições | versão do agregado na view |
| `Idempotency-Key` em POST | retries seguros do cliente | tabela de chaves processadas |
| Paginação por cursor | `OFFSET` fica lento em tabelas grandes | `WHERE (created_at, id) < (?, ?)` |
| OpenTelemetry | tracing distribuído HTTP → RabbitMQ → MongoDB | `spring-boot-starter-opentelemetry` |
| Prometheus + Grafana | dashboards e alertas | `micrometer-registry-prometheus` |
| CDC (Debezium) no lugar do polling | outbox em milissegundos | conector PostgreSQL → RabbitMQ |
| UUID v7 | índices B-tree mais eficientes | gerador ordenado por tempo |
| Testes de carga | conhecer limites reais | Gatling ou k6 |
| HATEOAS | nível 3 de Richardson | Spring HATEOAS |
| Modularização com Spring Modulith | verificação de módulos e eventos entre contextos | `spring-modulith-starter-core` |
