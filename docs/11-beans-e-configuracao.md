# 11. Beans, injeção de dependência e configuração no Spring

> **Resumo:** o Spring é um *contêiner de IoC*: ele cria os objetos (beans), resolve as
> dependências entre eles e gerencia o ciclo de vida. Neste projeto, **adaptadores** são beans por
> anotação (`@Component`, `@Repository`, `@RestController`) e **casos de uso** são beans declarados
> com `@Bean` em classes de configuração, para manter o núcleo livre do framework. Configuração
> externa vem de `application.yml` para records `@ConfigurationProperties` validados.

## IoC e DI em uma frase

**Inversão de controle:** quem cria e conecta os objetos não é o seu código, é o contêiner.
**Injeção de dependência:** cada objeto declara do que precisa (no construtor) e recebe pronto.

```java
// Sem DI: a classe decide a implementação e fica presa a ela
private final ShipmentRepository repo = new ShipmentPersistenceAdapter(new ...);

// Com DI: a classe declara a abstração; quem escolhe a implementação é a configuração
public CreateShipmentService(ShipmentRepository shipments, ...) { ... }
```

É isso que permite testar `CreateShipmentService` com um repositório em memória.

## Duas formas de registrar beans (e quando usar cada uma)

| | Anotações de estereótipo | Método `@Bean` |
|---|---|---|
| Como | `@Component`, `@Repository`, `@RestController`... na classe | método numa classe `@Configuration` que retorna o objeto |
| Descoberta | *component scan* (pacote da classe principal e subpacotes) | explícita |
| A classe conhece o Spring? | sim | **não** |
| Usado aqui em | adaptadores (já dependem de framework) | casos de uso (Java puro), domain service, `RestClient`, `Clock`, segurança |

```java
@Configuration(proxyBeanMethods = false)
public class ShippingConfig {

    @Bean
    FreightCalculator freightCalculator(ShippingProperties properties) {      // domain service
        return new FreightCalculator(properties.freight().toFreightTable());
    }

    @Bean
    CreateShipmentUseCase createShipmentUseCase(ShipmentRepository shipments,   // ← adaptador JPA
                                                PostalCodeLookup postalCodeLookup, // ← adaptador ViaCEP
                                                ShipmentEventPublisher eventPublisher,
                                                FreightCalculator freightCalculator,
                                                TransactionRunner transactions, Clock clock) {
        return new CreateShipmentService(shipments, postalCodeLookup, eventPublisher,
                freightCalculator, transactions, clock);
    }
}
```

- Os **parâmetros** de um método `@Bean` são injetados pelo Spring, por tipo.
- O **tipo de retorno** é a porta (`CreateShipmentUseCase`), então quem injeta depende da
  abstração.
- Lendo `ShippingConfig` você vê, num lugar só, como o contexto inteiro está conectado. É o
  *composition root*.

## Injeção por construtor (e por que não `@Autowired` em campo)

Todas as classes recebem dependências pelo construtor, em campos `final`:

- dependências explícitas: impossível criar o objeto sem elas;
- imutabilidade: os campos não mudam depois da construção;
- testável sem Spring: basta `new`;
- muitos parâmetros no construtor são um alerta visível de que a classe faz coisas demais.

Com um único construtor, nem é preciso `@Autowired`. A regra é verificada pelo ArchUnit
(`NO_CLASSES_SHOULD_USE_FIELD_INJECTION`).

## Resolução de dependências: tipo, nome e `@Qualifier`

O Spring injeta **por tipo**. Se houver mais de um bean do mesmo tipo, é preciso desambiguar:

```java
@Bean(ViaCepPostalCodeLookup.REST_CLIENT_BEAN)            // nome do bean: "viaCepRestClient"
public RestClient viaCepRestClient(RestClient.Builder builder, ViaCepProperties properties) { ... }

public ViaCepPostalCodeLookup(@Qualifier(REST_CLIENT_BEAN) RestClient restClient, ...) { ... }
```

Hoje só existe um `RestClient`, mas a segunda integração não vai precisar mexer nesta.

Outro exemplo: `@Qualifier("handlerExceptionResolver")` no
`ProblemDetailAuthenticationEntryPoint`, porque existem vários `HandlerExceptionResolver` no
contexto do Spring MVC.

## Escopos

| Escopo | Instâncias | Exemplo aqui |
|---|---|---|
| `singleton` (padrão) | uma por contêiner | todos os casos de uso, adaptadores, configurações |
| `prototype` | uma nova a cada injeção | `RestClient.Builder` (registrado pelo Spring Boot) |
| `request` / `session` | uma por requisição/sessão HTTP | não usado (API stateless) |

**Singleton precisa ser thread-safe:** a mesma instância atende todas as requisições ao mesmo
tempo. Por isso os serviços não guardam estado mutável, só dependências `final`.

**Por que o `RestClient.Builder` é prototype?** O builder é mutável. Se fosse singleton,
configurar a URL da ViaCEP nele "vazaria" para o próximo cliente construído. É o exemplo clássico
de quando o padrão (singleton) não serve.

## `@Configuration(proxyBeanMethods = false)`

Por padrão, classes `@Configuration` são estendidas via CGLIB para que um método `@Bean`
chamando outro receba o mesmo singleton. Quando nenhum método chama outro (o caso de todas aqui,
que recebem dependências por parâmetro), o proxy só custa tempo de subida. O próprio Spring Boot
usa `false` nas auto-configurações.

## Configuração externa: `@ConfigurationProperties` com records

```java
@Validated
@ConfigurationProperties(prefix = "app.integrations.viacep")
public record ViaCepProperties(@NotNull URI baseUrl,
                               @NotNull Duration connectTimeout,
                               @NotNull Duration readTimeout) { }
```

```yaml
app:
  integrations:
    viacep:
      base-url: ${VIACEP_BASE_URL:https://viacep.com.br}
      connect-timeout: 1s
      read-timeout: 2s
```

| Vantagem sobre `@Value("${...}")` espalhado | Como |
|---|---|
| Tipos reais | `Duration` aceita `1s`, `200ms`; `URI`; `Currency` aceita `BRL` |
| Validação na subida (fail fast) | `@Validated` + Bean Validation |
| Imutável | record |
| Autocomplete e documentação na IDE | `spring-boot-configuration-processor` gera os metadados |
| Agrupado e testável | `new ViaCepProperties(...)` nos testes |

**Convenção do projeto:** os valores ficam **só no YAML**; os records não têm `@DefaultValue`.
Padrão duplicado em dois lugares acaba divergindo.

**Registro explícito:** cada record é registrado com `@EnableConfigurationProperties(X.class)` na
configuração do seu assunto, e não com `@ConfigurationPropertiesScan` global. Assim, os testes de
fatia não exigem valores de propriedades que nem usam.

## Profiles

| Arquivo | Quando é carregado | Conteúdo |
|---|---|---|
| `application.yml` | sempre | toda a configuração; segredos **sem** padrão |
| `application-local.yml` | perfil `local` | segredo JWT de dev, operador de bootstrap, CORS para front-ends locais, log DEBUG |
| `application-test.yml` (em `src/test`) | perfil `test` | relay rápido, retries curtos, WireMock |

Profiles só **sobrescrevem** o que muda. A ordem de precedência (do mais fraco para o mais forte):
`application.yml` → `application-{profile}.yml` → variáveis de ambiente → argumentos de linha de
comando.

**Armadilha:** criar `src/test/resources/application.yml` *substitui* o de produção nos testes.
Por isso o arquivo de teste se chama `application-test.yml` e é ativado com
`@ActiveProfiles("test")`.

## Beans condicionais

| Anotação | Exemplo | Efeito |
|---|---|---|
| `@ConditionalOnProperty` | `OutboxRelayScheduler` (`app.outbox.relay.enabled`) | o agendamento só existe se ligado |
| `@ConditionalOnProperty` | `OperatorBootstrapRunner` (`app.bootstrap.operator.enabled`) | o bootstrap só roda se ligado |
| auto-configuração do Spring Boot | `JwtDecoder`, `MessageConverter` | ao definir o bean, o Boot recua (`@ConditionalOnMissingBean`) e usa o nosso |

## `@Enable*` fora da classe principal

`@EnableScheduling` (`OutboxConfig`), `@EnableCaching` (`CacheConfig`), `@EnableMethodSecurity`
(`SecurityConfig`)... nenhum fica em `LogisticsApplication`. Anotações na classe principal valem
também para os testes de fatia (`@WebMvcTest`, `@DataJpaTest`), que passariam a carregar
infraestrutura que não deveriam. Em classes `@Configuration` de assunto, só entram em ação no
contexto completo.

## Proxies e AOP: o que você precisa saber

Algumas anotações funcionam porque o Spring envolve o bean num **proxy**:
`@Cacheable` (`ViaCepPostalCodeLookup`), `@Transactional` (`OutboxShipmentEventPublisher`),
`@PreAuthorize` (`ShipmentController`).

**Armadilha clássica (*self-invocation*):** chamar um método anotado de dentro da própria classe
não passa pelo proxy, e a anotação é ignorada silenciosamente. Por isso as transações dos casos
de uso usam `TransactionTemplate` (explícito, sem proxy) via porta `TransactionRunner`, e a
composição retry/circuit breaker da ViaCEP é programática.

## Ciclo de vida e ordem

- `ApplicationRunner` (`OperatorBootstrapRunner`): roda depois que o contexto está pronto.
- `@Order(Ordered.HIGHEST_PRECEDENCE)` no `CorrelationIdFilter`: roda antes do filtro do Spring
  Security, para que até os 401 tenham correlation ID.
- `@Scheduled(initialDelay=...)`: o relay espera a aplicação terminar de subir.
- Beans `AutoCloseable` (containers do Testcontainers, por exemplo) são fechados no fim do
  contexto.

## Mapa de beans deste projeto

| Bean | Tipo de registro | Onde |
|---|---|---|
| Casos de uso (9) | `@Bean` | `IdentityConfig`, `ShippingConfig` |
| `FreightCalculator` | `@Bean` | `ShippingConfig` |
| Adaptadores de persistência | `@Repository` | `*PersistenceAdapter` |
| Adaptadores de integração | `@Component` | `ViaCepPostalCodeLookup`, `MongoTrackingHistoryStore`, `OutboxShipmentEventPublisher`... |
| Controllers | `@RestController` | `adapter.in.web` |
| `RestClient` (ViaCEP) | `@Bean` nomeado | `ViaCepClientConfig` |
| `JwtEncoder`, `JwtDecoder`, `PasswordEncoder` | `@Bean` | `JwtConfig` |
| `SecurityFilterChain`, `JwtAuthenticationConverter`, `CorsConfigurationSource` | `@Bean` | `SecurityConfig` |
| `Clock` | `@Bean` | `ClockConfig` |
| `MessageConverter` (JSON) | `@Bean` | `MessagingConfig` |
| Exchange, filas, bindings | `@Bean` (`Declarable`) | `ShippingMessagingConfig` |
| `OpenAPI`, `OpenApiCustomizer` (erros em RFC 9457 e 401 no contrato) | `@Bean` | `OpenApiConfig` |
| Propriedades (7 records) | `@EnableConfigurationProperties` | nas configs de cada assunto |
