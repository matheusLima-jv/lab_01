package com.matheuslima.logistics.shipping.adapter.in.messaging;

import com.matheuslima.logistics.shipping.adapter.messaging.ShipmentEventMessage;
import com.matheuslima.logistics.shipping.application.port.in.RecordTrackingEventCommand;
import com.matheuslima.logistics.shipping.application.port.in.RecordTrackingEventUseCase;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários do listener: a tradução "contrato de integração → comando do caso de uso".
 *
 * <p>A entrega real pelo RabbitMQ (conversão JSON, ack, retry, DLQ) é coberta pelo teste de ponta
 * a ponta. Aqui verificamos só a lógica do adaptador, sem broker.
 */
@DisplayName("TrackingProjectionListener")
class TrackingProjectionListenerTest {

    private final List<RecordTrackingEventCommand> received = new ArrayList<>();
    private boolean duplicate;
    private final RecordTrackingEventUseCase useCase = command -> {
        received.add(command);
        return !duplicate;
    };
    private final TrackingProjectionListener listener = new TrackingProjectionListener(useCase);

    private static ShipmentEventMessage message(String status, Map<String, String> details) {
        return new ShipmentEventMessage(1, UUID.randomUUID(), "shipment.cancelled", Instant.parse("2026-01-15T10:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(), status, details);
    }

    @Test
    @DisplayName("traduz a mensagem em comando, preservando IDs, status e instante")
    void translatesMessage() {
        ShipmentEventMessage message = message("CANCELLED", Map.of("reason", "Desistência"));

        listener.on(message);

        assertThat(received).singleElement().satisfies(command -> {
            assertThat(command.eventId()).isEqualTo(message.eventId());
            assertThat(command.shipmentId()).isEqualTo(message.shipmentId());
            assertThat(command.status()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(command.occurredAt()).isEqualTo(message.occurredAt());
            assertThat(command.details()).containsEntry("reason", "Desistência");
        });
    }

    @Test
    @DisplayName("remove valores nulos de details e aceita details ausente")
    void toleratesNullDetails() {
        Map<String, String> withNull = new HashMap<>();
        withNull.put("reason", null);
        withNull.put("other", "ok");

        listener.on(message("CANCELLED", withNull));
        listener.on(message("CANCELLED", null));

        assertThat(received.get(0).details()).containsExactly(Map.entry("other", "ok"));
        assertThat(received.get(1).details()).isEmpty();
    }

    @Test
    @DisplayName("duplicata não é erro: a mensagem é aceita (ack) normalmente")
    void duplicateIsNotAnError() {
        duplicate = true;

        listener.on(message("IN_TRANSIT", Map.of()));

        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("status desconhecido lança exceção (a mensagem segue para retry e depois DLQ)")
    void unknownStatusFails() {
        assertThatThrownBy(() -> listener.on(message("TELEPORTED", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(received).isEmpty();
    }
}
