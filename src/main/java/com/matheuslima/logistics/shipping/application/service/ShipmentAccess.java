package com.matheuslima.logistics.shipping.application.service;

import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shipping.application.ShipmentNotFoundException;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentRepository;
import com.matheuslima.logistics.shipping.domain.Shipment;
import com.matheuslima.logistics.shipping.domain.ShipmentId;

/**
 * Carrega um envio aplicando a regra de visibilidade ("cada cliente vê só os seus; operadores
 * veem todos").
 *
 * <h2>Por que extrair isso para uma classe?</h2>
 * Três casos de uso precisam exatamente da mesma regra (consultar, mudar status, ver histórico).
 * Se cada um a implementasse do seu jeito, bastaria um esquecer o filtro para abrir uma falha de
 * autorização (IDOR). Centralizar garante que a regra é uma só, testada uma vez, e que corrigi-la
 * corrige todos os usos.
 *
 * <p>É {@code final} com construtor privado (utilitário sem estado) e visível só dentro do
 * pacote: detalhe de implementação dos serviços, não API pública.
 */
final class ShipmentAccess {

    private ShipmentAccess() {
    }

    /**
     * @throws ShipmentNotFoundException se o envio não existir ou não for visível para quem pede.
     *         Os dois casos são indistinguíveis de propósito.
     */
    static Shipment loadVisibleTo(ShipmentRepository shipments, ShipmentId id, Requester requester) {
        return shipments.findById(id)
                .filter(shipment -> requester.canAccessResourceOwnedBy(shipment.customerId().value()))
                .orElseThrow(() -> new ShipmentNotFoundException(id));
    }
}
