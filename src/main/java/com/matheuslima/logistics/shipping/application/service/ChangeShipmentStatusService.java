package com.matheuslima.logistics.shipping.application.service;

import com.matheuslima.logistics.shared.application.ForbiddenOperationException;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shared.application.TransactionRunner;
import com.matheuslima.logistics.shipping.application.port.in.ChangeShipmentStatusCommand;
import com.matheuslima.logistics.shipping.application.port.in.ChangeShipmentStatusUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ShipmentView;
import com.matheuslima.logistics.shipping.application.port.in.StatusChange;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentEventPublisher;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentRepository;
import com.matheuslima.logistics.shipping.domain.Shipment;
import com.matheuslima.logistics.shipping.domain.ShipmentId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Caso de uso: despachar, entregar ou cancelar um envio.
 *
 * <h2>O roteiro clássico de um comando que altera um agregado</h2>
 * <ol>
 *   <li>Abre a transação.</li>
 *   <li>Carrega o agregado (aplicando a regra de visibilidade).</li>
 *   <li>Verifica a permissão para a ação.</li>
 *   <li>Chama o <b>método de negócio</b> do agregado, que valida a transição e registra o evento.
 *       Repare que o serviço não toca no status diretamente: ele pede, e o agregado decide.</li>
 *   <li>Salva o agregado e entrega os eventos ao publicador (outbox), na mesma transação.</li>
 * </ol>
 *
 * <h2>{@code switch} com <i>record patterns</i> (Java 21)</h2>
 * {@code case StatusChange.Cancel(String reason)} desestrutura o record e já entrega o campo.
 * Como {@link StatusChange} é selado, o compilador sabe que os três casos cobrem todas as
 * possibilidades: não há {@code default}, e uma quarta transição adicionada no futuro quebraria
 * a compilação aqui até ser tratada.
 */
public final class ChangeShipmentStatusService implements ChangeShipmentStatusUseCase {

    private final ShipmentRepository shipments;
    private final ShipmentEventPublisher eventPublisher;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ChangeShipmentStatusService(ShipmentRepository shipments, ShipmentEventPublisher eventPublisher,
                                       TransactionRunner transactions, Clock clock) {
        this.shipments = Objects.requireNonNull(shipments);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ShipmentView change(ChangeShipmentStatusCommand command) {
        Requester requester = command.requester();
        return transactions.inTransaction(() -> {
            Shipment shipment = ShipmentAccess.loadVisibleTo(shipments, ShipmentId.of(command.shipmentId()), requester);
            Instant now = clock.instant();

            switch (command.change()) {
                case StatusChange.Dispatch() -> {
                    requireOperator(requester, "despachar");
                    shipment.dispatch(now);
                }
                case StatusChange.Deliver() -> {
                    requireOperator(requester, "registrar a entrega de");
                    shipment.deliver(now);
                }
                case StatusChange.Cancel(String reason) -> shipment.cancel(reason, now);
            }

            shipments.save(shipment);
            eventPublisher.publish(shipment.pullDomainEvents());
            return ShipmentView.from(shipment);
        });
    }

    /**
     * Despachar e entregar são ações da operação física. Um cliente não pode marcar o próprio
     * pacote como entregue.
     */
    private static void requireOperator(Requester requester, String action) {
        if (!requester.operator()) {
            throw new ForbiddenOperationException("Somente operadores podem %s envios.".formatted(action));
        }
    }
}
