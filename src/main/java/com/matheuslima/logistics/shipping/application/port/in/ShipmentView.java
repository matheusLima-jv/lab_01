package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shipping.domain.Address;
import com.matheuslima.logistics.shipping.domain.Shipment;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Visão somente-leitura de um envio, devolvida pelos casos de uso.
 *
 * <p>Mesmo raciocínio de {@code UserView}: o agregado {@link Shipment} não sai do núcleo, e quem
 * está fora (controller) recebe uma fotografia imutável. Os tipos são simples (UUID, String,
 * BigDecimal) para que o adaptador web não precise conhecer os Value Objects para montar o JSON.
 *
 * <h2>Três modelos para o mesmo envio: não é exagero?</h2>
 * Domínio ({@code Shipment}), aplicação ({@code ShipmentView}) e web ({@code ShipmentResponse})
 * custam dois mapeamentos. Em troca, cada camada evolui sem quebrar as outras: renomear um campo
 * do JSON não toca no domínio, e refatorar o agregado não quebra a API pública. Para um projeto
 * descartável seria exagero; para uma base que vai crescer, é o que evita o "big ball of mud".
 */
public record ShipmentView(
        UUID id,
        UUID customerId,
        ShipmentStatus status,
        AddressView origin,
        AddressView destination,
        int weightGrams,
        BigDecimal freightAmount,
        String freightCurrency,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt) {

    public static ShipmentView from(Shipment shipment) {
        return new ShipmentView(
                shipment.id().value(),
                shipment.customerId().value(),
                shipment.status(),
                AddressView.from(shipment.origin()),
                AddressView.from(shipment.destination()),
                shipment.weight().grams(),
                shipment.freight().amount(),
                shipment.freight().currency().getCurrencyCode(),
                shipment.cancellationReason(),
                shipment.createdAt(),
                shipment.updatedAt());
    }

    /** Endereço achatado em Strings, pronto para serialização. */
    public record AddressView(String postalCode, String street, String number, String complement, String district,
                              String city, String state) {

        static AddressView from(Address address) {
            return new AddressView(address.postalCode().formatted(), address.street(), address.number(),
                    address.complement(), address.district(), address.city(), address.state().name());
        }
    }
}
