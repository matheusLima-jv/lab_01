package com.matheuslima.logistics.shared.infrastructure.outbox;

import com.matheuslima.logistics.shared.infrastructure.persistence.AssignedIdentifierEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma mensagem aguardando (ou que já passou pela) publicação no broker: linha da tabela
 * {@code outbox_messages}.
 *
 * <h2>Por que esta entidade é genérica (não sabe nada de "envios")?</h2>
 * O outbox é infraestrutura compartilhada. Cada contexto converte seus eventos de domínio em
 * {@code OutboxMessage} (com exchange, routing key e payload JSON prontos), e o relay só
 * transporta bytes de um lugar para outro. Um novo contexto que queira publicar eventos usa a
 * mesma tabela e o mesmo relay, sem nenhuma mudança aqui.
 *
 * <h2>Por que a entidade JPA tem comportamento ({@link #markPublished}, {@link #markFailed})?</h2>
 * Mesmo fora do domínio, encapsular as transições de estado evita que o relay manipule campos
 * soltos (ex.: esquecer de incrementar {@code attempts}). Pequenas classes coesas são boas em
 * qualquer camada.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage extends AssignedIdentifierEntity {

    /** Limite da coluna {@code last_error}; mensagens de erro maiores são truncadas. */
    static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 100)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    /**
     * JSON já serializado. {@code @JdbcTypeCode(SqlTypes.JSON)} diz ao Hibernate que a coluna é
     * JSON (JSONB no PostgreSQL); como o tipo Java é {@code String}, o conteúdo é gravado como
     * está, sem nova serialização.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    /** Exigido pelo JPA (o Hibernate instancia a entidade via reflexão). Não usar no código. */
    protected OutboxMessage() {
    }

    private OutboxMessage(UUID id, String aggregateType, UUID aggregateId, String exchange, String routingKey,
                          String payload, Instant occurredAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.aggregateType = Objects.requireNonNull(aggregateType);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.exchange = Objects.requireNonNull(exchange);
        this.routingKey = Objects.requireNonNull(routingKey);
        this.payload = Objects.requireNonNull(payload);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /**
     * Cria uma mensagem pendente.
     *
     * @param id         identificador da mensagem; use o {@code eventId} do evento de domínio para
     *                   que o mesmo ID percorra todo o caminho até o consumidor
     * @param occurredAt quando o fato ocorreu
     * @param createdAt  quando entrou no outbox (normalmente igual ou logo após {@code occurredAt})
     */
    public static OutboxMessage pending(UUID id, String aggregateType, UUID aggregateId, String exchange,
                                        String routingKey, String payload, Instant occurredAt, Instant createdAt) {
        return new OutboxMessage(id, aggregateType, aggregateId, exchange, routingKey, payload, occurredAt, createdAt);
    }

    /** O broker confirmou o recebimento: a mensagem sai da fila de pendências. */
    void markPublished(Instant when) {
        this.publishedAt = Objects.requireNonNull(when);
        this.attempts++;
        this.lastError = null;
    }

    /** A tentativa falhou: registra para diagnóstico; a mensagem continua pendente. */
    void markFailed(String error) {
        this.attempts++;
        String message = error == null ? "unknown error" : error;
        this.lastError = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
