# 0010. Spring Boot 4 e Java 21

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

O projeto servirá de base para outros. Começar numa geração anterior significaria herdar uma
migração logo no primeiro dia. Em outubro de 2026, a linha estável mais recente é o Spring Boot
4.1 (Spring Framework 7, Spring Security 7, Jakarta EE 11, Jackson 3, Hibernate 7, JUnit 6).

## Decisão

- **Spring Boot 4.1.1** e **Java 21** (LTS, versão mínima pedida; o código compila também em 25).
- Recursos modernos usados de forma intencional: records (VOs, DTOs, eventos), sealed interfaces e
  pattern matching em `switch` (eventos, transições), text blocks, virtual threads
  (`spring.threads.virtual.enabled`), `RestClient`, `RestTestClient`, `@MockitoBean`.

## Pontos de atenção da migração (úteis para quem vem do Boot 3)

| Mudança | Onde aparece |
|---|---|
| Starters modulares (`spring-boot-starter-webmvc`, `-flyway`, `-restclient`, `-*-test`) | `pom.xml` |
| Pacotes de testes de fatia mudaram (`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`...) | testes |
| Jackson 3 (`tools.jackson.*`; anotações continuam em `com.fasterxml.jackson.annotation`) | `MessagingConfig`, publicador |
| `spring.mongodb.uri` (antes `spring.data.mongodb.uri`) | `application.yml` |
| `spring.rabbitmq.listener.simple.retry.max-retries` (antes `max-attempts`) | `application.yml` |
| `spring.http.clients.*` para timeouts globais; `HttpClientSettings` no código | `ViaCepClientConfig` |
| Testcontainers 2: artefatos `testcontainers-*` e pacotes `org.testcontainers.postgresql...` | testes |
| `@MockBean` removido → `@MockitoBean` | testes de controller |

## Alternativas consideradas

- **Spring Boot 3.5:** mais material de referência na internet, mas já em fim de suporte OSS.
- **Java 25 (LTS mais recente):** possível; Java 21 foi mantido por ser o mínimo pedido e o mais
  difundido em empresas.

## Consequências

- ✅ Base atual, sem migração pendente.
- ❌ Menos exemplos públicos para algumas APIs novas; os comentários do código compensam.
