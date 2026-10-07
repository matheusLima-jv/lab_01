package com.matheuslima.logistics.shared.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Testes do envio com <i>publisher confirms</i>, simulando as respostas do broker.
 *
 * <p>Com um RabbitMQ real é difícil provocar um nack ou uma confirmação que nunca chega. Com o
 * {@link RabbitTemplate} mockado, controlamos exatamente o que o "broker" responde (completando
 * ou não o {@code CorrelationData}) e verificamos que cada cenário vira o resultado certo.
 */
@DisplayName("RabbitOutboxPublisher (publisher confirms)")
class RabbitOutboxPublisherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T10:00:00Z");

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitOutboxPublisher publisher = new RabbitOutboxPublisher(rabbitTemplate,
            new OutboxProperties(50, Duration.ofMillis(200),
                    new OutboxProperties.Relay(true, Duration.ZERO, Duration.ofSeconds(1))));

    private final OutboxMessage message = OutboxMessage.pending(UUID.randomUUID(), "Shipment", UUID.randomUUID(),
            "logistics.shipping.events", "shipment.created", "{\"status\":\"CREATED\"}", OCCURRED_AT, OCCURRED_AT);

    /** Simula a resposta do broker completando o future de confirmação. */
    private void brokerAnswers(boolean ack, String reason) {
        willAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).given(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("ack do broker: envia com as propriedades certas e retorna normalmente")
    void ack() {
        brokerAnswers(true, null);

        publisher.publish(message);

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("logistics.shipping.events"), eq("shipment.created"), sent.capture(),
                any(CorrelationData.class));
        var properties = sent.getValue().getMessageProperties();
        assertThat(new String(sent.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"status\":\"CREATED\"}");
        assertThat(properties.getMessageId()).isEqualTo(message.getId().toString());
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getType()).isEqualTo("shipment.created");
        assertThat(properties.getTimestamp().toInstant()).isEqualTo(OCCURRED_AT);
        assertThat((String) properties.getHeader("x-aggregate-type")).isEqualTo("Shipment");
    }

    @Test
    @DisplayName("nack do broker: falha para o relay tentar de novo depois")
    void nack() {
        brokerAnswers(false, "queue limit reached");

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(OutboxPublishException.class)
                .hasMessageContaining("nack")
                .hasMessageContaining("queue limit reached");
    }

    @Test
    @DisplayName("sem confirmação dentro do timeout: falha (não dá para afirmar que o broker recebeu)")
    void noConfirmation() {
        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(OutboxPublishException.class)
                .hasMessageContaining("No broker confirmation");
    }

    @Test
    @DisplayName("broker inacessível: a exceção de conexão vira OutboxPublishException")
    void brokerDown() {
        willThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .given(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(OutboxPublishException.class)
                .hasCauseInstanceOf(AmqpConnectException.class);
    }

    @Test
    @DisplayName("thread interrompida: restaura a flag de interrupção e falha")
    void interrupted() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> publisher.publish(message))
                    .isInstanceOf(OutboxPublishException.class)
                    .hasMessageContaining("Interrupted");
            assertThat(Thread.currentThread().isInterrupted()).as("flag de interrupção preservada").isTrue();
        } finally {
            Thread.interrupted(); // limpa a flag para não afetar outros testes
        }
    }
}
