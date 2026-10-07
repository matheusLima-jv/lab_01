# Registros de Decisão de Arquitetura (ADRs)

Um ADR registra **uma** decisão relevante: o contexto em que foi tomada, o que foi decidido, as
alternativas consideradas e as consequências, inclusive as negativas. Formato de Michael Nygard,
adaptado.

**Por que escrever ADRs?** Daqui a um ano, ninguém vai lembrar *por que* o projeto usa outbox em
vez de publicar direto no RabbitMQ. Sem o registro, a decisão parece arbitrária e alguém a
"simplifica", reintroduzindo o problema que ela resolvia. ADRs são curtos, versionados junto com
o código e imutáveis: uma decisão revista ganha um ADR novo que substitui o anterior.

| # | Decisão | Status |
|---|---|---|
| [0001](0001-arquitetura-hexagonal-com-ddd.md) | Arquitetura hexagonal com DDD tático | Aceita |
| [0002](0002-monolito-modular-com-bounded-contexts.md) | Monólito modular com dois bounded contexts | Aceita |
| [0003](0003-casos-de-uso-sem-framework.md) | Casos de uso sem anotações do Spring (beans via `@Bean` e `TransactionRunner`) | Aceita |
| [0004](0004-jwt-autoassinado-hs256.md) | JWT autoassinado com HS256 e Spring Security Resource Server | Aceita |
| [0005](0005-postgresql-e-mongodb.md) | PostgreSQL como fonte da verdade e MongoDB como read model | Aceita |
| [0006](0006-outbox-transacional.md) | Transactional Outbox para publicar eventos no RabbitMQ | Aceita |
| [0007](0007-resiliencia-na-integracao-viacep.md) | Timeout, retry, circuit breaker e cache na integração com a ViaCEP | Aceita |
| [0008](0008-testcontainers-em-vez-de-h2.md) | Testcontainers em vez de banco em memória nos testes | Aceita |
| [0009](0009-idioma-codigo-ingles-documentacao-portugues.md) | Código em inglês, documentação e mensagens em português | Aceita |
| [0010](0010-spring-boot-4-e-java-21.md) | Spring Boot 4 e Java 21 | Aceita |

## Modelo para novos ADRs

```markdown
# NNNN. Título no imperativo

- **Status:** Proposta | Aceita | Substituída por NNNN
- **Data:** AAAA-MM-DD

## Contexto
Qual problema, quais forças e restrições.

## Decisão
O que foi decidido, de forma direta.

## Alternativas consideradas
O que mais foi avaliado e por que não foi escolhido.

## Consequências
O que fica melhor, o que fica pior e o que precisa ser monitorado.
```
