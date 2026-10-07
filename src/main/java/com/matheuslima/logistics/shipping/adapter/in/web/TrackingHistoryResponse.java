package com.matheuslima.logistics.shipping.adapter.in.web;

import com.matheuslima.logistics.shipping.application.port.in.TrackingEntryView;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Linha do tempo de rastreamento de um envio.
 *
 * <p>Os eventos vêm em um objeto (e não como array solto na raiz do JSON) por um motivo prático
 * de evolução: dá para acrescentar campos depois (ex.: previsão de entrega) sem quebrar clientes.
 * Um array na raiz não tem onde receber campos novos.
 *
 * @param shipmentId envio
 * @param events     eventos em ordem cronológica
 */
public record TrackingHistoryResponse(UUID shipmentId, List<EventResponse> events) {

    static TrackingHistoryResponse from(UUID shipmentId, List<TrackingEntryView> entries) {
        return new TrackingHistoryResponse(shipmentId, entries.stream().map(EventResponse::from).toList());
    }

    public record EventResponse(UUID eventId, ShipmentStatus status, String description, Instant occurredAt) {

        static EventResponse from(TrackingEntryView view) {
            return new EventResponse(view.eventId(), view.status(), view.description(), view.occurredAt());
        }
    }
}
