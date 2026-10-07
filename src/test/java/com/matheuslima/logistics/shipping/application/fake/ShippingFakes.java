package com.matheuslima.logistics.shipping.application.fake;

import com.matheuslima.logistics.shared.application.ExternalServiceUnavailableException;
import com.matheuslima.logistics.shared.application.PageQuery;
import com.matheuslima.logistics.shared.application.PageResult;
import com.matheuslima.logistics.shared.application.fake.DirectTransactionRunner;
import com.matheuslima.logistics.shipping.application.port.out.PostalCodeDetails;
import com.matheuslima.logistics.shipping.application.port.out.PostalCodeLookup;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentEventPublisher;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentRepository;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentSearchCriteria;
import com.matheuslima.logistics.shipping.application.port.out.TrackingEntry;
import com.matheuslima.logistics.shipping.application.port.out.TrackingHistoryStore;
import com.matheuslima.logistics.shipping.domain.Address;
import com.matheuslima.logistics.shipping.domain.PostalCode;
import com.matheuslima.logistics.shipping.domain.Shipment;
import com.matheuslima.logistics.shipping.domain.ShipmentEvent;
import com.matheuslima.logistics.shipping.domain.ShipmentId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementações em memória das portas de saída do contexto de envios.
 */
public final class ShippingFakes {

    private ShippingFakes() {
    }

    /**
     * Repositório em memória que guarda <b>cópias</b> dos agregados.
     *
     * <p>Detalhe importante: se guardássemos a própria instância, um serviço que esquecesse de
     * chamar {@code save} passaria no teste, porque a alteração "apareceria" no repositório por
     * referência. Copiando na escrita e na leitura, o fake se comporta como um banco de verdade:
     * só o que foi salvo existe.
     */
    public static class InMemoryShipmentRepository implements ShipmentRepository {

        private final Map<ShipmentId, Shipment> rows = new LinkedHashMap<>();

        @Override
        public void save(Shipment shipment) {
            rows.put(shipment.id(), copyOf(shipment));
        }

        @Override
        public Optional<Shipment> findById(ShipmentId id) {
            return Optional.ofNullable(rows.get(id)).map(InMemoryShipmentRepository::copyOf);
        }

        @Override
        public PageResult<Shipment> search(ShipmentSearchCriteria criteria, PageQuery page) {
            List<Shipment> matching = rows.values().stream()
                    .filter(s -> criteria.customer().map(s::belongsTo).orElse(true))
                    .filter(s -> criteria.statusFilter().map(status -> s.status() == status).orElse(true))
                    .sorted(Comparator.comparing(Shipment::createdAt).reversed())
                    .toList();
            List<Shipment> pageItems = matching.stream()
                    .skip(page.offset())
                    .limit(page.size())
                    .map(InMemoryShipmentRepository::copyOf)
                    .toList();
            return new PageResult<>(pageItems, page.page(), page.size(), matching.size());
        }

        public int count() {
            return rows.size();
        }

        private static Shipment copyOf(Shipment s) {
            return Shipment.restore(s.id(), s.customerId(), s.origin(), s.destination(), s.weight(), s.freight(),
                    s.status(), s.cancellationReason(), s.createdAt(), s.updatedAt());
        }
    }

    /**
     * Fonte de CEP configurável. Registra se foi chamada dentro de uma transação, para que os
     * testes possam verificar a regra "integração externa fora da transação".
     */
    public static class FakePostalCodeLookup implements PostalCodeLookup {

        private final Map<PostalCode, PostalCodeDetails> known = new HashMap<>();
        private final DirectTransactionRunner transactions;
        private boolean unavailable;
        private boolean calledInsideTransaction;
        private int calls;

        public FakePostalCodeLookup(DirectTransactionRunner transactions) {
            this.transactions = transactions;
        }

        /** Torna o CEP do endereço "conhecido", devolvendo a localidade desse endereço. */
        public FakePostalCodeLookup knows(Address address) {
            known.put(address.postalCode(), new PostalCodeDetails(address.postalCode(), address.street(),
                    address.district(), address.city(), address.state()));
            return this;
        }

        /** Simula a ViaCEP fora do ar (timeout, 5xx ou circuit breaker aberto). */
        public void goOffline() {
            this.unavailable = true;
        }

        @Override
        public Optional<PostalCodeDetails> lookup(PostalCode postalCode) {
            calls++;
            calledInsideTransaction |= transactions.isActive();
            if (unavailable) {
                throw new ExternalServiceUnavailableException("ViaCEP", new RuntimeException("simulated outage"));
            }
            return Optional.ofNullable(known.get(postalCode));
        }

        public boolean wasCalledInsideTransaction() {
            return calledInsideTransaction;
        }

        public int calls() {
            return calls;
        }
    }

    /** Publicador que acumula os eventos e verifica que a publicação ocorre DENTRO da transação. */
    public static class RecordingShipmentEventPublisher implements ShipmentEventPublisher {

        private final List<ShipmentEvent> published = new ArrayList<>();
        private final DirectTransactionRunner transactions;
        private boolean publishedOutsideTransaction;

        public RecordingShipmentEventPublisher(DirectTransactionRunner transactions) {
            this.transactions = transactions;
        }

        @Override
        public void publish(List<ShipmentEvent> events) {
            publishedOutsideTransaction |= !transactions.isActive();
            published.addAll(events);
        }

        public List<ShipmentEvent> published() {
            return List.copyOf(published);
        }

        public boolean wasPublishedOutsideTransaction() {
            return publishedOutsideTransaction;
        }
    }

    /** Histórico em memória com a mesma semântica idempotente do adaptador MongoDB. */
    public static class InMemoryTrackingHistoryStore implements TrackingHistoryStore {

        private final Map<UUID, TrackingEntry> entries = new LinkedHashMap<>();

        @Override
        public boolean append(TrackingEntry entry) {
            return entries.putIfAbsent(entry.eventId(), entry) == null;
        }

        @Override
        public List<TrackingEntry> findByShipmentId(ShipmentId shipmentId) {
            return entries.values().stream()
                    .filter(entry -> entry.shipmentId().equals(shipmentId))
                    .sorted(Comparator.comparing(TrackingEntry::occurredAt))
                    .toList();
        }

        public int size() {
            return entries.size();
        }
    }
}
