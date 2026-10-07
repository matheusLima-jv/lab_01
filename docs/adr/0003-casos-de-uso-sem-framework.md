# 0003. Casos de uso sem anotações do Spring

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

Os serviços da camada de aplicação precisam de transação e de injeção de dependência. O caminho
comum é anotá-los com `@Service` e `@Transactional`. Mas isso acopla o núcleo ao Spring e torna a
fronteira de transação implícita (o método inteiro), incluindo chamadas lentas à ViaCEP, que
prenderiam uma conexão do pool durante a espera.

## Decisão

- Serviços de aplicação são **Java puro** (sem `@Service`, sem `@Transactional`).
- São registrados como beans por métodos `@Bean` em `IdentityConfig` e `ShippingConfig`
  (*composition root*), retornando o tipo da porta.
- Transações via porta **`TransactionRunner`** (`inTransaction`, `inReadOnlyTransaction`),
  implementada com `TransactionTemplate` no `SpringTransactionRunner`, que também traduz o
  conflito de bloqueio otimista para `ConcurrentUpdateException`.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| `@Service` + `@Transactional` nos serviços | núcleo depende do Spring; a transação engloba a chamada HTTP; armadilhas de proxy (self-invocation) |
| `@Transactional` com `REQUIRES_NEW` em métodos privados auxiliares | não funciona (proxy) e esconde a fronteira |
| AOP declarativo por pointcut na configuração | sem anotação no núcleo, mas "mágico" e difícil de enxergar |

## Consequências

- ✅ Casos de uso testados com `new` e fakes, sem contexto Spring.
- ✅ Fronteira de transação visível: a ViaCEP é chamada fora; a gravação + outbox, dentro (há
  teste verificando).
- ✅ ArchUnit pode proibir Spring na camada de aplicação sem exceções.
- ❌ Um método `@Bean` por caso de uso (boilerplate de configuração).
- ❌ Menos familiar para quem espera `@Transactional`.
