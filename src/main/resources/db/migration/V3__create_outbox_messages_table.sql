-- =============================================================================
-- V3: outbox transacional
-- =============================================================================
-- O padrão Transactional Outbox resolve o problema da "escrita dupla": gravar o
-- envio no PostgreSQL e publicar o evento no RabbitMQ não pode ser atômico (são
-- sistemas diferentes). Solução: o evento é gravado AQUI, na mesma transação do
-- envio. Um processo separado (OutboxRelay) lê as mensagens pendentes, publica no
-- broker e marca como publicadas. Se a aplicação cair no meio, a mensagem
-- continua pendente e é publicada na próxima rodada (entrega "pelo menos uma vez").
-- =============================================================================

CREATE TABLE outbox_messages (
    -- Mesmo valor do eventId do evento de domínio: vira o message-id no RabbitMQ
    -- e a chave de idempotência dos consumidores.
    id             UUID         NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    -- Destino no broker: o relay é genérico e não precisa saber nada de "envios".
    exchange       VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(100) NOT NULL,
    -- JSONB: o PostgreSQL valida que o conteúdo é JSON e permite consultá-lo em
    -- investigações (ex.: payload->>'shipmentId'), o que um TEXT não permitiria.
    payload        JSONB        NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    -- NULL = pendente. Preenchido quando o broker CONFIRMA o recebimento
    -- (publisher confirms), não apenas quando a mensagem foi enviada.
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),

    CONSTRAINT pk_outbox_messages PRIMARY KEY (id)
);

-- Índice PARCIAL: indexa só as linhas pendentes. Depois de publicadas, as linhas
-- saem do índice, que fica pequeno e rápido mesmo com milhões de mensagens já
-- publicadas na tabela. É exatamente o filtro da consulta do relay.
CREATE INDEX ix_outbox_messages_pending ON outbox_messages (created_at) WHERE published_at IS NULL;

COMMENT ON TABLE outbox_messages IS 'Outbox transacional: eventos aguardando publicação no RabbitMQ.';
