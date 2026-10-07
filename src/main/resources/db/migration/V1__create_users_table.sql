-- =============================================================================
-- V1: usuários (contexto de identidade)
-- =============================================================================
-- Por que Flyway (migrações versionadas) e não "ddl-auto=update" do Hibernate?
--   * Cada mudança de schema vira um arquivo revisável em code review, com ordem
--     garantida e histórico (tabela flyway_schema_history).
--   * O "update" do Hibernate nunca remove colunas, não cria índices complexos,
--     não migra dados e pode fazer alterações perigosas em produção sem aviso.
--   * O Hibernate fica com "ddl-auto=validate": na subida, ele só confere se o
--     mapeamento das entidades bate com o schema. Divergência = a aplicação não
--     sobe (fail fast), em vez de quebrar na primeira requisição.
--
-- Regra de ouro: uma migração aplicada NUNCA é editada. Correções viram V2, V3...
-- O Flyway guarda o checksum de cada arquivo e acusa erro se um já aplicado mudar.
-- =============================================================================

CREATE TABLE users (
    -- UUID gerado pela aplicação (ver UserId): permite conhecer o ID antes do INSERT
    -- e não expõe sequências previsíveis.
    id            UUID         NOT NULL,
    -- 254 = limite prático de um e-mail (RFC 5321). Guardado já normalizado
    -- (minúsculo) pelo Value Object Email.
    email         VARCHAR(254) NOT NULL,
    -- Hash no formato do DelegatingPasswordEncoder: "{bcrypt}$2a$10$..." (~68 chars).
    -- 100 dá folga para algoritmos com hashes maiores no futuro.
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    -- TIMESTAMPTZ guarda um instante absoluto (normalizado em UTC). TIMESTAMP sem
    -- fuso é uma "data de calendário" ambígua: 10:00 de onde?
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    -- A constraint é a garantia REAL de unicidade (a checagem no caso de uso é só
    -- o caminho rápido). O nome é referenciado pelo adaptador de persistência para
    -- traduzir a violação em EmailAlreadyRegisteredException.
    CONSTRAINT uk_users_email UNIQUE (email),
    -- CHECK no banco: defesa extra contra dados inválidos vindos de fora da
    -- aplicação (scripts, correções manuais).
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'OPERATOR'))
);

COMMENT ON TABLE users IS 'Usuários do sistema (contexto de identidade).';
COMMENT ON COLUMN users.password_hash IS 'Hash da senha com prefixo do algoritmo. Nunca guardar senha em texto puro.';
