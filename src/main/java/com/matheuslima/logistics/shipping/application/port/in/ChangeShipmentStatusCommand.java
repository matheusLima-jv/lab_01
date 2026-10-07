package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shared.application.Requester;

import java.util.Objects;
import java.util.UUID;

/**
 * Pedido de mudança de estado de um envio.
 *
 * @param requester  quem pede
 * @param shipmentId envio alvo
 * @param change     qual transição aplicar
 */
public record ChangeShipmentStatusCommand(Requester requester, UUID shipmentId, StatusChange change) {

    public ChangeShipmentStatusCommand {
        Objects.requireNonNull(requester, "requester must not be null");
        Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        Objects.requireNonNull(change, "change must not be null");
    }
}
