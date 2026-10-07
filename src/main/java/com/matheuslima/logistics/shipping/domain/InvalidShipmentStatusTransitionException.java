package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.DomainException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;

/**
 * Tentativa de mover um envio para um estado não permitido a partir do estado atual
 * (ex.: cancelar um envio já entregue).
 *
 * <p>Categoria {@link ErrorCategory#CONFLICT} (HTTP 409): a requisição é válida em si, mas
 * conflita com o estado atual do recurso. Esse é exatamente o significado de 409 na RFC 9110.
 * Repetir a mesma requisição não vai funcionar enquanto o estado não mudar.
 */
public class InvalidShipmentStatusTransitionException extends DomainException {

    public InvalidShipmentStatusTransitionException(ShipmentId id, ShipmentStatus from, ShipmentStatus to) {
        super(ErrorCategory.CONFLICT, "SHIPMENT_INVALID_STATUS_TRANSITION",
                "O envio %s não pode passar de %s para %s.".formatted(id, from, to));
    }
}
