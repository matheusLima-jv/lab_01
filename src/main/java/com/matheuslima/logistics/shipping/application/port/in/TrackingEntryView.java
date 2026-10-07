package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shipping.application.port.out.TrackingEntry;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha do histórico de rastreamento, como exposta aos adaptadores de entrada.
 *
 * <p>Não expõe o mapa {@code details} nem o {@code recordedAt} do armazenamento: são dados
 * internos da projeção. A view publica só o que a linha do tempo precisa mostrar.
 */
public record TrackingEntryView(UUID eventId, ShipmentStatus status, String description, Instant occurredAt) {

    public static TrackingEntryView from(TrackingEntry entry) {
        return new TrackingEntryView(entry.eventId(), entry.status(), entry.description(), entry.occurredAt());
    }
}
