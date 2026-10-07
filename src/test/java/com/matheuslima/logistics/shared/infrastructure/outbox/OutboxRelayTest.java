package com.matheuslima.logistics.shared.infrastructure.outbox;

import com.matheuslima.logistics.shared.application.fake.DirectTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testes unitários do relay do outbox, com o broker simulado (Mockito).
 *
 * <p>O caminho feliz com RabbitMQ real é coberto pelo teste de ponta a ponta. Aqui testamos o que
 * é difícil de reproduzir com containers: o broker recusando ou sem responder, e o relay parando
 * o lote para não segurar a transação à toa.
 */
@DisplayName("OutboxRelay")
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
    private final RabbitOutboxPublisher publisher = mock(RabbitOutboxPublisher.class);
    private final DirectTransactionRunner transactions = new DirectTransactionRunner();
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(50, Duration.ofSeconds(5),
                new OutboxProperties.Relay(true, Duration.ofSeconds(2), Duration.ofSeconds(1)));
        relay = new OutboxRelay(repository, publisher, transactions, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OutboxMessage pending() {
        return OutboxMessage.pending(UUID.randomUUID(), "Shipment", UUID.randomUUID(), "exchange", "shipment.created",
                "{}", NOW, NOW);
    }

    @Test
    @DisplayName("publica o lote e marca cada mensagem como publicada, dentro de uma transação")
    void publishesBatch() {
        OutboxMessage first = pending();
        OutboxMessage second = pending();
        given(repository.lockNextPendingBatch(50)).willReturn(List.of(first, second));

        int published = relay.relayPendingMessages();

        assertThat(published).isEqualTo(2);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isEqualTo(NOW);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(transactions.committedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("na primeira falha, registra o erro e interrompe o lote (broker provavelmente fora)")
    void stopsBatchOnFirstFailure() {
        OutboxMessage ok = pending();
        OutboxMessage failing = pending();
        OutboxMessage untouched = pending();
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of(ok, failing, untouched));
        willThrow(new OutboxPublishException("No broker confirmation within PT5S")).given(publisher).publish(failing);

        int published = relay.relayPendingMessages();

        assertThat(published).isEqualTo(1);
        assertThat(ok.getPublishedAt()).isNotNull();
        assertThat(failing.getPublishedAt()).isNull();
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getLastError()).contains("No broker confirmation");
        assertThat(untouched.getAttempts()).isZero();
        verify(publisher, never()).publish(untouched);
    }

    @Test
    @DisplayName("mensagens de erro enormes são truncadas para caber na coluna")
    void truncatesLongErrors() {
        OutboxMessage message = pending();

        message.markFailed("x".repeat(2_000));
        message.markFailed(null);

        assertThat(message.getLastError()).isEqualTo("unknown error");
        assertThat(message.getAttempts()).isEqualTo(2);
        message.markFailed("y".repeat(2_000));
        assertThat(message.getLastError()).hasSize(OutboxMessage.MAX_ERROR_LENGTH);
    }

    @Test
    @DisplayName("sem mensagens pendentes, não publica nada")
    void emptyBatch() {
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of());

        assertThat(relay.relayPendingMessages()).isZero();
    }
}
