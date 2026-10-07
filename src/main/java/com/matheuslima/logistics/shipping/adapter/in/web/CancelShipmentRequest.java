package com.matheuslima.logistics.shipping.adapter.in.web;

import com.matheuslima.logistics.shipping.domain.Shipment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/shipments/{id}/cancel}.
 *
 * @param reason motivo do cancelamento (obrigatório)
 */
public record CancelShipmentRequest(
        @Schema(example = "Cliente desistiu da compra")
        @NotBlank @Size(max = Shipment.MAX_CANCELLATION_REASON_LENGTH)
        String reason) {
}
