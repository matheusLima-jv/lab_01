package com.matheuslima.logistics;

import com.matheuslima.logistics.shipping.adapter.messaging.ShipmentEventMessage;
import com.matheuslima.logistics.shipping.adapter.messaging.ShippingMessagingProperties;
import com.matheuslima.logistics.shipping.application.port.out.TrackingHistoryStore;
import com.matheuslima.logistics.shipping.domain.ShipmentId;
import com.matheuslima.logistics.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Garantias da mensageria com um RabbitMQ real: as propriedades que fazem um sistema
 * orientado a eventos ser confiável.
 */
@DisplayName("Mensageria (RabbitMQ real)")
class MessagingIT extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ShippingMessagingProperties messaging;

    @Autowired
    private TrackingHistoryStore trackingHistory;

    @Test
    @DisplayName("idempotência: a mesma mensagem entregue duas vezes gera uma única entrada no histórico")
    void duplicateDeliveryIsIdempotent() {
        UUID shipmentId = UUID.randomUUID();
        ShipmentEventMessage message = new ShipmentEventMessage(1, UUID.randomUUID(), "shipment.dispatched",
                Instant.now().truncatedTo(ChronoUnit.MILLIS), shipmentId, UUID.randomUUID(), "IN_TRANSIT", Map.of());

        rabbitTemplate.convertAndSend(messaging.exchange(), message.eventType(), message);
        rabbitTemplate.convertAndSend(messaging.exchange(), message.eventType(), message);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(trackingHistory.findByShipmentId(ShipmentId.of(shipmentId))).hasSize(1));
        // Dá tempo para a segunda entrega ser processada e confirma que nada mudou.
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(trackingHistory.findByShipmentId(ShipmentId.of(shipmentId))).hasSize(1));
    }

    @Test
    @DisplayName("mensagem venenosa: após as retentativas, vai para a DLQ em vez de travar a fila")
    void poisonMessageGoesToDeadLetterQueue() {
        Message poison = MessageBuilder.withBody("{ isto não é json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId("poison-" + UUID.randomUUID())
                .build();

        rabbitTemplate.send(messaging.exchange(), "shipment.created", poison);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message deadLettered = rabbitTemplate.receive(messaging.trackingDeadLetterQueue(), 500);
            assertThat(deadLettered).isNotNull();
            assertThat(deadLettered.getMessageProperties().getMessageId()).isEqualTo(poison.getMessageProperties().getMessageId());
        });
    }
}
