package com.matheuslima.logistics.shipping.application.service;

import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shared.application.TransactionRunner;
import com.matheuslima.logistics.shipping.application.port.in.GetTrackingHistoryUseCase;
import com.matheuslima.logistics.shipping.application.port.in.TrackingEntryView;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentRepository;
import com.matheuslima.logistics.shipping.application.port.out.TrackingHistoryStore;
import com.matheuslima.logistics.shipping.domain.ShipmentId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: histórico de rastreamento de um envio.
 *
 * <h2>Duas fontes, papéis diferentes</h2>
 * <ol>
 *   <li><b>PostgreSQL (fonte da verdade):</b> confirma que o envio existe e que quem pede pode
 *       vê-lo. A autorização nunca é decidida pelo read model.</li>
 *   <li><b>MongoDB (read model):</b> fornece a linha do tempo pronta para exibição.</li>
 * </ol>
 * A leitura do Mongo fica fora da transação JPA de propósito: o MongoDB não participa da
 * transação do PostgreSQL (não há transação distribuída aqui), então colocá-la "dentro" só
 * prenderia a conexão do Postgres sem garantir nada.
 */
public final class GetTrackingHistoryService implements GetTrackingHistoryUseCase {

    private final ShipmentRepository shipments;
    private final TrackingHistoryStore trackingHistory;
    private final TransactionRunner transactions;

    public GetTrackingHistoryService(ShipmentRepository shipments, TrackingHistoryStore trackingHistory,
                                     TransactionRunner transactions) {
        this.shipments = Objects.requireNonNull(shipments);
        this.trackingHistory = Objects.requireNonNull(trackingHistory);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public List<TrackingEntryView> history(Requester requester, UUID shipmentId) {
        ShipmentId id = ShipmentId.of(shipmentId);
        transactions.inReadOnlyTransaction(() -> ShipmentAccess.loadVisibleTo(shipments, id, requester));
        return trackingHistory.findByShipmentId(id).stream()
                .map(TrackingEntryView::from)
                .toList();
    }
}
