-- =============================================================================
-- V2: envios (contexto de logística)
-- =============================================================================

CREATE TABLE shipments (
    id                      UUID          NOT NULL,
    -- Referência ao usuário dono, SEM foreign key para users. É intencional:
    -- users pertence a outro bounded context (identidade). Em DDD, contextos se
    -- referenciam por identificador, não por integridade referencial do banco.
    -- Isso permite separar os contextos em bancos ou serviços diferentes no futuro
    -- sem refazer o modelo. A integridade é garantida pela aplicação: o customer_id
    -- vem sempre do token autenticado.
    customer_id             UUID          NOT NULL,
    status                  VARCHAR(20)   NOT NULL,

    -- Endereços como colunas "achatadas" (Embeddable no JPA) e não como tabela
    -- separada: um endereço de envio é um Value Object, sem identidade própria e
    -- sem ciclo de vida independente do envio. Juntá-lo à linha evita JOINs.
    --
    -- VARCHAR + CHECK em vez de CHAR(n): a documentação do PostgreSQL deixa claro
    -- que char(n) não é mais rápido (costuma ser o mais lento, pelo preenchimento
    -- com espaços). O formato exato é garantido pelas constraints CHECK abaixo.
    origin_postal_code      VARCHAR(8)    NOT NULL,
    origin_street           VARCHAR(255)  NOT NULL,
    origin_number           VARCHAR(20)   NOT NULL,
    origin_complement       VARCHAR(100),
    origin_district         VARCHAR(255)  NOT NULL,
    origin_city             VARCHAR(255)  NOT NULL,
    origin_state            VARCHAR(2)    NOT NULL,

    destination_postal_code VARCHAR(8)    NOT NULL,
    destination_street      VARCHAR(255)  NOT NULL,
    destination_number      VARCHAR(20)   NOT NULL,
    destination_complement  VARCHAR(100),
    destination_district    VARCHAR(255)  NOT NULL,
    destination_city        VARCHAR(255)  NOT NULL,
    destination_state       VARCHAR(2)    NOT NULL,

    weight_grams            INTEGER       NOT NULL,
    -- NUMERIC (decimal exato) para dinheiro, nunca REAL/DOUBLE PRECISION.
    -- 12 dígitos com 2 casas: até 9.999.999.999,99.
    freight_amount          NUMERIC(12, 2) NOT NULL,
    freight_currency        VARCHAR(3)    NOT NULL,
    cancellation_reason     VARCHAR(255),
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    -- Coluna de bloqueio otimista (@Version). Cada UPDATE faz
    -- "SET version = version + 1 WHERE id = ? AND version = ?".
    version                 BIGINT        NOT NULL,

    CONSTRAINT pk_shipments PRIMARY KEY (id),
    CONSTRAINT ck_shipments_status CHECK (status IN ('CREATED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_shipments_weight CHECK (weight_grams BETWEEN 1 AND 30000),
    CONSTRAINT ck_shipments_freight CHECK (freight_amount >= 0),
    -- CHECK com expressão regular (operador ~ do PostgreSQL): CEP com 8 dígitos.
    CONSTRAINT ck_shipments_origin_cep CHECK (origin_postal_code ~ '^[0-9]{8}$'),
    CONSTRAINT ck_shipments_destination_cep CHECK (destination_postal_code ~ '^[0-9]{8}$'),
    -- Invariante do domínio repetida no banco: só cancelados têm motivo.
    CONSTRAINT ck_shipments_cancellation CHECK (
        (status = 'CANCELLED' AND cancellation_reason IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancellation_reason IS NULL)
    )
);

-- Índices desenhados a partir das consultas reais (e não "por via das dúvidas"):
--   1) "meus envios, mais recentes primeiro": WHERE customer_id = ? ORDER BY created_at DESC
CREATE INDEX ix_shipments_customer_created ON shipments (customer_id, created_at DESC);
--   2) operador filtrando por status: WHERE status = ? ORDER BY created_at DESC
CREATE INDEX ix_shipments_status_created ON shipments (status, created_at DESC);

COMMENT ON TABLE shipments IS 'Envios (agregado Shipment). Fonte da verdade do estado de cada envio.';
COMMENT ON COLUMN shipments.customer_id IS 'ID do usuário dono (contexto de identidade). Sem FK por design (bounded contexts).';
