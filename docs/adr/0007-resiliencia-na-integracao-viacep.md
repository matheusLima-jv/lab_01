# 0007. Timeout, retry, circuit breaker e cache na integração com a ViaCEP

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

Criar um envio depende de uma API pública de terceiros (ViaCEP), sem SLA, que durante o próprio
desenvolvimento apresentou conexões resetadas. Uma dependência lenta, sem proteção, esgota
threads e conexões e derruba a aplicação inteira.

## Decisão

- **Timeouts** explícitos no `RestClient` dedicado (connect 1 s, read 2 s).
- **Retry** (Resilience4j): 2 tentativas, backoff exponencial, só para falhas transitórias.
- **Circuit breaker** (Resilience4j): abre com ≥ 50% de falhas em 10 chamadas (mínimo 5); 30 s
  aberto.
- Composição **programática** `Retry(CircuitBreaker(chamada))`, com configuração no YAML.
- **Cache** Caffeine (24 h, só resultados encontrados).
- Contrato da porta separando "CEP não existe" (`Optional.empty()` → 422) de "fonte indisponível"
  (exceção → 503 + `Retry-After`).
- Chamada externa **fora** da transação de banco.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Anotações `@Retry`/`@CircuitBreaker` | funcionam, mas escondem a ordem em proxies AOP e separam a tradução de exceções |
| Spring Framework 7 `@Retryable` nativo | não tem circuit breaker nem métricas integradas como o Resilience4j |
| Redis como cache | dado público e barato; cache local basta |
| Sem cache | toda criação de envio pagaria duas chamadas externas |

## Consequências

- ✅ Pior caso de latência limitado (~4,2 s por CEP) e falha rápida com o circuito aberto.
- ✅ CEPs conhecidos continuam funcionando com a ViaCEP fora do ar.
- ✅ Estado do circuito e métricas no Actuator.
- ❌ Mais configuração para entender e ajustar.
- ⚠️ Parâmetros devem ser revistos com dados reais de latência (p99) em produção.
