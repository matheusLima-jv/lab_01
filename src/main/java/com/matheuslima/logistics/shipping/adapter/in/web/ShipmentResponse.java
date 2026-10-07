package com.matheuslima.logistics.shipping.adapter.in.web;

import com.matheuslima.logistics.shipping.application.port.in.ShipmentView;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação JSON de um envio.
 *
 * <h2>Decisões de formato</h2>
 * <ul>
 *   <li><b>Dinheiro como objeto {@code {amount, currency}}:</b> o valor nunca anda sem a moeda.
 *       O {@code amount} sai como número decimal com 2 casas ({@code 30.00}).</li>
 *   <li><b>Datas em ISO-8601 UTC</b> ({@code 2026-01-15T10:00:00Z}): sem ambiguidade de fuso. O
 *       cliente converte para o fuso de quem está vendo.</li>
 *   <li><b>Status como texto</b> ({@code "IN_TRANSIT"}): legível e estável.</li>
 * </ul>
 */
public record ShipmentResponse(
        UUID id,
        UUID customerId,
        ShipmentStatus status,
        AddressResponse origin,
        AddressResponse destination,
        int weightGrams,
        MoneyResponse freight,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt) {

    static ShipmentResponse from(ShipmentView view) {
        return new ShipmentResponse(
                view.id(),
                view.customerId(),
                view.status(),
                AddressResponse.from(view.origin()),
                AddressResponse.from(view.destination()),
                view.weightGrams(),
                new MoneyResponse(view.freightAmount(), view.freightCurrency()),
                view.cancellationReason(),
                view.createdAt(),
                view.updatedAt());
    }

    public record AddressResponse(String postalCode, String street, String number, String complement,
                                  String district, String city, String state) {

        static AddressResponse from(ShipmentView.AddressView view) {
            return new AddressResponse(view.postalCode(), view.street(), view.number(), view.complement(),
                    view.district(), view.city(), view.state());
        }
    }

    public record MoneyResponse(BigDecimal amount, String currency) {
    }
}
