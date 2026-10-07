# 0005. PostgreSQL como fonte da verdade e MongoDB como read model

- **Status:** Aceita
- **Data:** 2026-10-07

## Contexto

Os dados de usuários e envios exigem transações, constraints e consistência forte. O histórico de
rastreamento é diferente: só cresce, é consultado por envio, cada tipo de evento tem detalhes
distintos e pode ser reconstruído a partir dos eventos. O projeto também deve exercitar um banco
NoSQL.

## Decisão

- **PostgreSQL** para usuários, envios e outbox (fonte da verdade), com Flyway e JPA.
- **MongoDB** para `tracking_entries`, uma **projeção CQRS** alimentada pelos eventos via
  RabbitMQ: um documento por evento, `_id` = ID do evento (idempotência), índice
  `(shipmentId, occurredAt)`.
- A autorização de acesso ao histórico é sempre decidida no PostgreSQL, nunca no read model.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Tudo no PostgreSQL (tabela de histórico) | tecnicamente suficiente; não exercitaria NoSQL nem a separação comando/consulta |
| MongoDB como fonte da verdade | perde constraints e transações relacionais, que as regras do envio usam |
| Event sourcing completo | complexidade alta (replay, snapshots, versionamento) para pouco ganho aqui |
| Array de eventos dentro de um documento por envio | update condicional para idempotência; documento sem limite de tamanho |

## Consequências

- ✅ Cada banco no papel que faz melhor; read model descartável e reconstruível.
- ✅ Esquema flexível nos detalhes dos eventos.
- ❌ Dois bancos para operar, monitorar e versionar.
- ❌ Consistência eventual no histórico (alguns instantes de atraso), documentada na API.
- ⚠️ Para este volume, o PostgreSQL resolveria sozinho. A escolha é deliberadamente didática e
  justificada pelo formato e pelo papel dos dados, e não por desempenho.
