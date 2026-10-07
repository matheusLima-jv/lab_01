# 0006. Transactional Outbox para publicar eventos no RabbitMQ

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

Cada mudança de estado de um envio precisa gerar um evento no RabbitMQ. Gravar no PostgreSQL e
publicar no broker são duas escritas em sistemas diferentes, sem transação comum (*dual write*):
qualquer ordem perde ou inventa eventos se a aplicação falhar entre os passos.

## Decisão

- Eventos são gravados na tabela `outbox_messages` (JSONB) **na mesma transação** do agregado,
  pelo adaptador `OutboxShipmentEventPublisher` (`propagation = MANDATORY`).
- O `OutboxRelay` publica em lotes, a cada 1 s, com `FOR UPDATE SKIP LOCKED` (várias instâncias
  sem coordenador) e **publisher confirms** (só marca como publicada após o ack do broker).
- Consumidores são **idempotentes** (deduplicação pelo ID do evento).
- Contrato de mensagem separado do evento de domínio (`ShipmentEventMessage`, versionado).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Publicar direto no caso de uso | perde ou inventa eventos em falhas |
| `@TransactionalEventListener(AFTER_COMMIT)` | o evento se perde se a aplicação cair logo após o commit |
| Transação distribuída (XA/2PC) | complexa, lenta e não suportada de forma prática pelo RabbitMQ |
| CDC com Debezium | latência menor, mas exige infraestrutura extra (Kafka Connect); fica como evolução |
| Spring Modulith event publication registry | resolve o mesmo problema, mas esconderia o mecanismo que o projeto quer ensinar |

## Consequências

- ✅ Nenhum evento perdido: estado e evento são atômicos.
- ✅ Funciona com o broker fora do ar; a fila de pendências escoa quando ele volta.
- ❌ Entrega "pelo menos uma vez": duplicatas são possíveis (resolvido pela idempotência).
- ❌ Até ~1 s de latência pelo polling; ordem global não garantida entre instâncias.
- ⚠️ Monitorar `outbox_messages` com `published_at IS NULL` antigas e `last_error` preenchido.
