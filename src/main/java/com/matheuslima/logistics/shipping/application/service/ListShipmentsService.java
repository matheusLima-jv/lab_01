package com.matheuslima.logistics.shipping.application.service;

import com.matheuslima.logistics.shared.application.PageResult;
import com.matheuslima.logistics.shared.application.TransactionRunner;
import com.matheuslima.logistics.shipping.application.port.in.ListShipmentsQuery;
import com.matheuslima.logistics.shipping.application.port.in.ListShipmentsUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ShipmentView;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentRepository;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentSearchCriteria;
import com.matheuslima.logistics.shipping.domain.CustomerId;

import java.util.Objects;

/**
 * Caso de uso: listar envios.
 *
 * <p>A regra de escopo vira um <b>critério de consulta</b> e não um filtro em memória: para um
 * cliente, o {@code WHERE customer_id = ?} vai para o banco. Filtrar depois de buscar quebraria a
 * paginação (páginas com menos itens que o pedido) e traria para a memória dados que o cliente
 * nem pode ver.
 */
public final class ListShipmentsService implements ListShipmentsUseCase {

    private final ShipmentRepository shipments;
    private final TransactionRunner transactions;

    public ListShipmentsService(ShipmentRepository shipments, TransactionRunner transactions) {
        this.shipments = Objects.requireNonNull(shipments);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public PageResult<ShipmentView> list(ListShipmentsQuery query) {
        ShipmentSearchCriteria criteria = query.requester().operator()
                ? ShipmentSearchCriteria.allCustomers(query.status())
                : ShipmentSearchCriteria.ofCustomer(CustomerId.of(query.requester().userId()), query.status());

        return transactions.inReadOnlyTransaction(() ->
                shipments.search(criteria, query.page()).map(ShipmentView::from));
    }
}
