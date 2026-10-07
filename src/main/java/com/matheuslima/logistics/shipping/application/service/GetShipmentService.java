package com.matheuslima.logistics.shipping.application.service;

import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shared.application.TransactionRunner;
import com.matheuslima.logistics.shipping.application.port.in.GetShipmentUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ShipmentView;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentRepository;
import com.matheuslima.logistics.shipping.domain.ShipmentId;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: consultar um envio.
 *
 * <p>A conversão para {@link ShipmentView} acontece <b>dentro</b> da transação de leitura. Com
 * entidades JPA que têm relacionamentos {@code LAZY}, ler um campo depois de fechar a transação
 * causa {@code LazyInitializationException}. Este modelo não tem relacionamentos lazy, mas
 * mapear dentro da transação é o hábito seguro.
 */
public final class GetShipmentService implements GetShipmentUseCase {

    private final ShipmentRepository shipments;
    private final TransactionRunner transactions;

    public GetShipmentService(ShipmentRepository shipments, TransactionRunner transactions) {
        this.shipments = Objects.requireNonNull(shipments);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public ShipmentView get(Requester requester, UUID shipmentId) {
        return transactions.inReadOnlyTransaction(() ->
                ShipmentView.from(ShipmentAccess.loadVisibleTo(shipments, ShipmentId.of(shipmentId), requester)));
    }
}
