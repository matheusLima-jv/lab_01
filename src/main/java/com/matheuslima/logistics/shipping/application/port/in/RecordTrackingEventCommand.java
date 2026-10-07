package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shipping.domain.ShipmentStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de envio a registrar no histórico, já traduzido da mensagem do broker pelo adaptador.
 *
 * @param eventId    identificador do evento (chave de idempotência)
 * @param shipmentId envio ao qual o evento se refere
 * @param status     status resultante
 * @param occurredAt quando o fato ocorreu (não quando a mensagem chegou)
 * @param details    dados específicos do tipo de evento (ex.: cidades, motivo do cancelamento)
 */
public record RecordTrackingEventCommand(UUID eventId, UUID shipmentId, ShipmentStatus status, Instant occurredAt,
                                         Map<String, String> details) {

    public RecordTrackingEventCommand {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
