# 09. Estratégia de testes

> **Resumo:** pirâmide de testes. A base são muitos testes rápidos de domínio e aplicação, sem
> Spring nem Docker. No meio ficam testes de adaptadores com fatias do Spring, WireMock e bancos
> reais via Testcontainers. No topo, poucos testes de ponta a ponta. Regras de arquitetura são
> testadas com ArchUnit, e a cobertura mínima é exigida pelo JaCoCo no `mvn verify`.

## Como rodar

```bash
./mvnw test               # rápidos (Surefire, *Test): não precisam de Docker
./mvnw verify             # + integração (Failsafe, *IT) com Testcontainers + gate de cobertura
./mvnw verify -Djacoco.skip=true            # sem o gate de cobertura
./mvnw test -Dtest=ShipmentTest             # uma classe
./mvnw verify -Dit.test=ShipmentJourneyIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false
```

Relatórios: `target/site/jacoco/index.html` (cobertura), `target/surefire-reports` e
`target/failsafe-reports`.

**Convenção de nomes:** `*Test` roda só na JVM (segundos); `*IT` precisa de Docker. É a divisão
padrão do Maven (Surefire x Failsafe) e permite feedback rápido no dia a dia.

## A pirâmide

```
                 ▲   ponta a ponta (11)        @SpringBootTest + RestTestClient + Testcontainers
                ▲▲▲  integração (12)           @DataJpaTest / @DataMongoTest + Testcontainers
              ▲▲▲▲▲▲ adaptadores (~60)          @WebMvcTest, WireMock, Mockito, ArchUnit
       ▲▲▲▲▲▲▲▲▲▲▲▲▲▲ domínio + aplicação (~140)  JUnit + AssertJ + fakes (sem Spring)
```

Quanto mais alto, mais lento, mais frágil e mais caro para diagnosticar. Por isso as **regras**
são testadas embaixo (milissegundos), e os testes de cima só provam que as peças funcionam juntas.

## Camada por camada

### 1. Domínio: testes unitários puros

`ShipmentTest`, `ShipmentStatusTest`, `ValueObjectsTest`, `FreightCalculatorTest`,
`IdentityDomainTest`, `SharedKernelTest`.

- Sem Spring, sem mocks: `new` e asserções.
- **Tabelas de exemplos** com `@ParameterizedTest`: a máquina de estados inteira (16 pares) e os
  exemplos de frete calculados à mão. Tabelas são fáceis de revisar com quem é dono da regra.
- **Object Mother** (`ShippingFixtures`): endereços reais reutilizados, para os testes focarem no
  comportamento e não na montagem.
- Relógio fixo (`NOW`): nada depende da hora em que o teste roda.

### 2. Aplicação: casos de uso com fakes

`IdentityUseCasesTest`, `ShippingUseCasesTest`.

As portas de saída são substituídas por **fakes** (implementações em memória: `ShippingFakes`,
`IdentityFakes`, `DirectTransactionRunner`), e não por mocks.

| | Fake | Mock (Mockito) |
|---|---|---|
| Verifica | estado e comportamento | interações ("chamou X com Y") |
| Robustez a refactor | alta | baixa (acoplado ao "como") |
| Melhor uso | portas com semântica (repositório, store) | quando a interação **é** o contrato (controller → caso de uso) |

Os fakes também verificam **decisões de arquitetura**: o `FakePostalCodeLookup` registra se foi
chamado dentro de uma transação, e o `RecordingShipmentEventPublisher` registra se publicou fora
dela. Assim os testes garantem que "a ViaCEP é chamada fora da transação" e que "eventos são
publicados dentro dela".

O `InMemoryShipmentRepository` guarda **cópias** dos agregados: um serviço que esquecesse de
chamar `save()` passaria no teste se o fake guardasse a mesma instância.

### 3. Adaptadores de entrada: fatias web

`ShipmentControllerTest`, `IdentityControllersTest` com a anotação composta `@WebAdapterTest`:

- `@WebMvcTest` sobe só a camada web, **com a segurança real** (`SecurityConfig`, `JwtConfig`) e o
  tratamento de erros real.
- Casos de uso como `@MockitoBean`: o foco é o **contrato HTTP** (status, JSON, headers,
  validação, autorização).
- `jwt()` do `spring-security-test` simula usuários autenticados sem gerar tokens.
- Casos importantes: *mass assignment* (`"role": "OPERATOR"` ignorado), IDOR (404), 401 com
  `WWW-Authenticate`, 403 do `@PreAuthorize`, 503 com `Retry-After`, 500 sem vazar detalhes,
  correlation ID malicioso substituído.

Também na base: `TrackingProjectionListenerTest`, `OperatorBootstrapRunnerTest`,
`OutboxRelayTest` e `RabbitOutboxPublisherTest` (ack, nack, timeout, broker fora e interrupção,
cenários difíceis de reproduzir com um broker real).

### 4. Adaptadores de saída: infraestrutura real

| Teste | Infraestrutura | O que só um banco real revela |
|---|---|---|
| `ShipmentPersistenceAdapterIT` | PostgreSQL (Testcontainers) | migrações + mapeamento, Specifications, ordenação/paginação, CHECKs, **bloqueio otimista com duas transações reais** |
| `UserPersistenceAdapterIT` | PostgreSQL | violação de `UNIQUE` traduzida em 409 |
| `OutboxShipmentEventPublisherIT` | PostgreSQL | payload gravado como JSONB consultável (`->>`), `propagation = MANDATORY` |
| `MongoTrackingHistoryStoreIT` | MongoDB | idempotência por `_id`, ordenação, índice criado |
| `ViaCepPostalCodeLookupTest` | WireMock | HTTP real: retry, timeout, conexão resetada, circuit breaker |
| `SecurityAdaptersTest` | — | JWT: expirado, forjado, adulterado, outro emissor, `alg: none` |

**Por que Testcontainers e não H2?** H2 não tem `JSONB`, `FOR UPDATE SKIP LOCKED`, índice parcial
nem `~` (regex). Testar em H2 seria testar outro banco: verde no teste, vermelho em produção. Ver
[ADR 0008](adr/0008-testcontainers-em-vez-de-h2.md).

### 5. Ponta a ponta

`ShipmentJourneyIT`, `MessagingIT` e `OpenApiContractIT`, todos estendendo
`AbstractIntegrationTest`:

- Aplicação inteira num Tomcat real (`RANDOM_PORT`) + PostgreSQL, MongoDB e RabbitMQ reais + ViaCEP
  no WireMock.
- **Awaitility** para a consistência eventual (outbox → RabbitMQ → MongoDB): repete a verificação
  até passar ou estourar o tempo. Nunca `Thread.sleep` chutado.
- Jornada completa, cache, 422/503, isolamento entre clientes, conflito de status, segurança com
  token real, mensagem duplicada (idempotência) e mensagem venenosa (DLQ).
- **Contrato OpenAPI** (`OpenApiContractIT`): lê o documento publicado em `/v3/api-docs` e aplica
  regras gerais a todas as operações. Toda operação documenta um 2xx, todo erro é
  `application/problem+json` e só cadastro e login são públicos. Por serem regras gerais, um
  endpoint novo que esquecer o 2xx quebra o build sem que alguém precise escrever um teste para
  ele. O teste nasceu de um defeito real: com qualquer `@ApiResponse` declarado, o springdoc
  deixa de gerar o 200, e quatro operações ficaram sem resposta de sucesso no documento.

**Contexto único:** todas as classes ponta a ponta têm a mesma configuração, então o Spring
reaproveita o contexto e os containers. Por isso cada teste cria os próprios dados (e-mails
únicos) e não depende de banco vazio. O WireMock é um singleton (`ViaCepStub`) pelo mesmo motivo:
um WireMock por classe quebraria o contexto em cache, que apontaria para a porta antiga.

### 6. Smoke test manual: `docs/api/requests.http`

Um roteiro de 42 requisições com asserts, executado contra uma API de verdade (ViaCEP real
inclusive), no IntelliJ, no terminal com a CLI `ijhttp` ou no VS Code com o httpYac. Serve para
explorar a API e conferir um ambiente recém-subido.

**O que ele NÃO é:** parte do build. Depende de internet e de uma API no ar, então não roda no
`mvn verify` nem no CI. A garantia automatizada continua sendo a pirâmide acima. Pelo mesmo
motivo, ele herda a instabilidade da ViaCEP: se ela falhar nas duas tentativas do retry, a
criação responde 503 (o comportamento projetado) e os asserts seguintes falham em cascata. Rode de
novo. Os testes automatizados usam WireMock justamente para não depender disso.

## Testes de arquitetura (ArchUnit)

`ArchitectureTest` transforma o desenho em regras verificadas:

- camadas apontando para dentro (`layeredArchitecture`);
- domínio e aplicação sem Spring/JPA/Jackson;
- adaptadores de entrada não usam adaptadores de saída;
- `identity` e `shipping` não se conhecem; `shared` não conhece nenhum dos dois;
- sem ciclos entre pacotes;
- convenções: serviços em `application.service` terminando com `Service`, controllers em
  `adapter.in.web`, entidades JPA fora do núcleo;
- sem injeção por campo, sem `System.out`, sem `java.util.logging`.

As regras foram validadas por **mutação**: violações deliberadas (domínio importando Spring,
controller usando repositório) fazem o teste falhar com mensagens claras. Uma regra que nunca
falha pode estar testando nada.

## Cobertura (JaCoCo)

- Mede unitários (`jacoco.exec`) **e** integração (`jacoco-it.exec`), junta os dois e verifica.
- Gate: **90% de linhas e 80% de branches**. Atualmente ~99% / ~86%.
- **O que NÃO é verdade:** "cobertura alta = testes bons". Cobertura só mostra o que nenhum teste
  executa. Um teste sem asserção "cobre" linhas. A qualidade vem das asserções e dos cenários.

## Boas práticas usadas (e por quê)

| Prática | Exemplo |
|---|---|
| `@DisplayName` em português, como especificação | "não permite cancelar depois do despacho (409)" |
| `@Nested` agrupando por comportamento | `ShipmentTest.Cancellation` |
| AssertJ fluente com mensagens | `assertThat(x).as("motivo").isEqualTo(y)` |
| Dados únicos por teste | `UUID.randomUUID()` em e-mails |
| Uma razão para falhar por teste | testes pequenos e nomeados pelo comportamento |
| Mockito como `-javaagent` | o JDK 21 avisa (e versões futuras vão bloquear) agentes anexados dinamicamente (JEP 451) |
