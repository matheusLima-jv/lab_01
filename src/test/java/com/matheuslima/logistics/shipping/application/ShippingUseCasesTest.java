package com.matheuslima.logistics.shipping.application;

import com.matheuslima.logistics.shared.application.ExternalServiceUnavailableException;
import com.matheuslima.logistics.shared.application.ForbiddenOperationException;
import com.matheuslima.logistics.shared.application.PageQuery;
import com.matheuslima.logistics.shared.application.PageResult;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shared.application.fake.DirectTransactionRunner;
import com.matheuslima.logistics.shared.domain.DomainValidationException;
import com.matheuslima.logistics.shipping.ShippingFixtures;
import com.matheuslima.logistics.shipping.application.fake.ShippingFakes.FakePostalCodeLookup;
import com.matheuslima.logistics.shipping.application.fake.ShippingFakes.InMemoryShipmentRepository;
import com.matheuslima.logistics.shipping.application.fake.ShippingFakes.InMemoryTrackingHistoryStore;
import com.matheuslima.logistics.shipping.application.fake.ShippingFakes.RecordingShipmentEventPublisher;
import com.matheuslima.logistics.shipping.application.port.in.ChangeShipmentStatusCommand;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentCommand;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentCommand.AddressInput;
import com.matheuslima.logistics.shipping.application.port.in.ListShipmentsQuery;
import com.matheuslima.logistics.shipping.application.port.in.RecordTrackingEventCommand;
import com.matheuslima.logistics.shipping.application.port.in.ShipmentView;
import com.matheuslima.logistics.shipping.application.port.in.StatusChange;
import com.matheuslima.logistics.shipping.application.port.in.TrackingEntryView;
import com.matheuslima.logistics.shipping.application.port.out.TrackingEntry;
import com.matheuslima.logistics.shipping.application.service.ChangeShipmentStatusService;
import com.matheuslima.logistics.shipping.application.service.CreateShipmentService;
import com.matheuslima.logistics.shipping.application.service.GetShipmentService;
import com.matheuslima.logistics.shipping.application.service.GetTrackingHistoryService;
import com.matheuslima.logistics.shipping.application.service.ListShipmentsService;
import com.matheuslima.logistics.shipping.application.service.RecordTrackingEventService;
import com.matheuslima.logistics.shipping.domain.InvalidShipmentStatusTransitionException;
import com.matheuslima.logistics.shipping.domain.ShipmentEvent;
import com.matheuslima.logistics.shipping.domain.ShipmentId;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;
import com.matheuslima.logistics.shipping.domain.freight.FreightCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.matheuslima.logistics.shipping.ShippingFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes dos casos de uso de envio.
 *
 * <p>Além do comportamento, estes testes verificam <b>decisões de arquitetura</b> com a ajuda
 * dos fakes:
 * <ul>
 *   <li>a ViaCEP é consultada fora da transação;</li>
 *   <li>os eventos são publicados dentro da mesma transação que grava o envio (outbox);</li>
 *   <li>a regra de visibilidade devolve "não encontrado" para envios de outros clientes.</li>
 * </ul>
 * Se alguém refatorar e quebrar uma dessas garantias, o teste aponta na hora.
 */
@DisplayName("Casos de uso de envio")
class ShippingUseCasesTest {

    private final DirectTransactionRunner transactions = new DirectTransactionRunner();
    private final InMemoryShipmentRepository shipments = new InMemoryShipmentRepository();
    private final FakePostalCodeLookup postalCodes = new FakePostalCodeLookup(transactions)
            .knows(ShippingFixtures.saoPauloSe())
            .knows(ShippingFixtures.rioDeJaneiro())
            .knows(ShippingFixtures.manaus());
    private final RecordingShipmentEventPublisher publisher = new RecordingShipmentEventPublisher(transactions);
    private final InMemoryTrackingHistoryStore tracking = new InMemoryTrackingHistoryStore();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final CreateShipmentService create = new CreateShipmentService(shipments, postalCodes, publisher,
            new FreightCalculator(ShippingFixtures.defaultFreightTable()), transactions, clock);
    private final ChangeShipmentStatusService changeStatus = new ChangeShipmentStatusService(shipments, publisher,
            transactions, clock);
    private final GetShipmentService get = new GetShipmentService(shipments, transactions);
    private final ListShipmentsService list = new ListShipmentsService(shipments, transactions);
    private final GetTrackingHistoryService history = new GetTrackingHistoryService(shipments, tracking, transactions);
    private final RecordTrackingEventService record = new RecordTrackingEventService(tracking, clock);

    private final UUID customerId = UUID.randomUUID();
    private final Requester customer = Requester.customer(customerId);
    private final Requester otherCustomer = Requester.customer(UUID.randomUUID());
    private final Requester operator = Requester.operatorUser(UUID.randomUUID());

    private ShipmentView createSpToRio() {
        return create.create(new CreateShipmentCommand(customer,
                new AddressInput("01001-000", "100", "Sala 1"),
                new AddressInput("20040020", "10", null),
                1_500));
    }

    @Nested
    @DisplayName("criação")
    class Create {

        @Test
        @DisplayName("enriquece endereços pela fonte de CEP, calcula o frete e grava")
        void createsShipment() {
            ShipmentView view = createSpToRio();

            assertThat(view.status()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(view.customerId()).isEqualTo(customerId);
            assertThat(view.origin().city()).isEqualTo("São Paulo");
            assertThat(view.origin().street()).isEqualTo("Praça da Sé");
            assertThat(view.origin().number()).isEqualTo("100");
            assertThat(view.origin().complement()).isEqualTo("Sala 1");
            assertThat(view.origin().postalCode()).isEqualTo("01001-000");
            assertThat(view.destination().state()).isEqualTo("RJ");
            assertThat(view.freightAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(view.freightCurrency()).isEqualTo("BRL");
            assertThat(view.createdAt()).isEqualTo(NOW);
            assertThat(shipments.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("consulta a ViaCEP FORA da transação e publica eventos DENTRO dela")
        void respectsTransactionBoundaries() {
            createSpToRio();

            assertThat(postalCodes.calls()).isEqualTo(2);
            assertThat(postalCodes.wasCalledInsideTransaction()).isFalse();
            assertThat(publisher.wasPublishedOutsideTransaction()).isFalse();
            assertThat(publisher.published()).singleElement().isInstanceOf(ShipmentEvent.Created.class);
        }

        @Test
        @DisplayName("CEP inexistente gera PostalCodeNotFoundException indicando origem ou destino")
        void unknownPostalCode() {
            var command = new CreateShipmentCommand(customer, new AddressInput("01001-000", "1", null),
                    new AddressInput("99999-999", "1", null), 1_000);

            assertThatThrownBy(() -> create.create(command))
                    .isInstanceOf(PostalCodeNotFoundException.class)
                    .hasMessageContaining("destino")
                    .hasMessageContaining("99999-999");
            assertThat(shipments.count()).isZero();
            assertThat(publisher.published()).isEmpty();
        }

        @Test
        @DisplayName("ViaCEP fora do ar propaga indisponibilidade (503) e nada é gravado")
        void lookupUnavailable() {
            postalCodes.goOffline();

            assertThatThrownBy(ShippingUseCasesTest.this::createSpToRio)
                    .isInstanceOf(ExternalServiceUnavailableException.class);
            assertThat(shipments.count()).isZero();
        }

        @Test
        @DisplayName("peso inválido falha ANTES de gastar chamadas externas")
        void invalidWeightFailsFast() {
            var command = new CreateShipmentCommand(customer, new AddressInput("01001-000", "1", null),
                    new AddressInput("20040-020", "1", null), 40_000);

            assertThatThrownBy(() -> create.create(command)).isInstanceOf(DomainValidationException.class);
            assertThat(postalCodes.calls()).isZero();
        }
    }

    @Nested
    @DisplayName("mudança de status")
    class ChangeStatus {

        @Test
        @DisplayName("operador despacha e entrega; cada transição publica um evento")
        void operatorDispatchesAndDelivers() {
            ShipmentView created = createSpToRio();

            changeStatus.change(new ChangeShipmentStatusCommand(operator, created.id(), new StatusChange.Dispatch()));
            ShipmentView delivered = changeStatus.change(
                    new ChangeShipmentStatusCommand(operator, created.id(), new StatusChange.Deliver()));

            assertThat(delivered.status()).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(get.get(customer, created.id()).status()).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(publisher.published())
                    .extracting(ShipmentEvent::resultingStatus)
                    .containsExactly(ShipmentStatus.CREATED, ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED);
            assertThat(publisher.wasPublishedOutsideTransaction()).isFalse();
        }

        @Test
        @DisplayName("cliente não pode despachar nem o próprio envio (403)")
        void customerCannotDispatch() {
            ShipmentView created = createSpToRio();

            assertThatThrownBy(() -> changeStatus.change(
                    new ChangeShipmentStatusCommand(customer, created.id(), new StatusChange.Dispatch())))
                    .isInstanceOf(ForbiddenOperationException.class);
            assertThatThrownBy(() -> changeStatus.change(
                    new ChangeShipmentStatusCommand(customer, created.id(), new StatusChange.Deliver())))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        @DisplayName("dono cancela informando o motivo")
        void ownerCancels() {
            ShipmentView created = createSpToRio();

            ShipmentView cancelled = changeStatus.change(new ChangeShipmentStatusCommand(customer, created.id(),
                    new StatusChange.Cancel("Comprei errado")));

            assertThat(cancelled.status()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(cancelled.cancellationReason()).isEqualTo("Comprei errado");
        }

        @Test
        @DisplayName("outro cliente recebe 404, como se o envio não existisse")
        void otherCustomerGetsNotFound() {
            ShipmentView created = createSpToRio();

            assertThatThrownBy(() -> changeStatus.change(new ChangeShipmentStatusCommand(otherCustomer, created.id(),
                    new StatusChange.Cancel("tentativa indevida"))))
                    .isInstanceOf(ShipmentNotFoundException.class);
        }

        @Test
        @DisplayName("transição inválida não grava nada nem publica evento")
        void invalidTransitionChangesNothing() {
            ShipmentView created = createSpToRio();
            int eventsBefore = publisher.published().size();

            assertThatThrownBy(() -> changeStatus.change(
                    new ChangeShipmentStatusCommand(operator, created.id(), new StatusChange.Deliver())))
                    .isInstanceOf(InvalidShipmentStatusTransitionException.class);
            assertThat(get.get(operator, created.id()).status()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(publisher.published()).hasSize(eventsBefore);
        }
    }

    @Nested
    @DisplayName("consultas")
    class Queries {

        @Test
        @DisplayName("cliente lista apenas os próprios envios; operador lista todos")
        void listScopesByRequester() {
            createSpToRio();
            createSpToRio();
            create.create(new CreateShipmentCommand(otherCustomer, new AddressInput("01001000", "1", null),
                    new AddressInput("69005010", "2", null), 500));

            PageResult<ShipmentView> mine = list.list(new ListShipmentsQuery(customer, null, PageQuery.firstPage()));
            PageResult<ShipmentView> all = list.list(new ListShipmentsQuery(operator, null, PageQuery.firstPage()));

            assertThat(mine.totalItems()).isEqualTo(2);
            assertThat(mine.items()).allMatch(s -> s.customerId().equals(customerId));
            assertThat(all.totalItems()).isEqualTo(3);
        }

        @Test
        @DisplayName("filtra por status e pagina")
        void filtersAndPaginates() {
            ShipmentView first = createSpToRio();
            createSpToRio();
            createSpToRio();
            changeStatus.change(new ChangeShipmentStatusCommand(customer, first.id(), new StatusChange.Cancel("x")));

            PageResult<ShipmentView> cancelled = list.list(
                    new ListShipmentsQuery(customer, ShipmentStatus.CANCELLED, PageQuery.firstPage()));
            PageResult<ShipmentView> secondPage = list.list(
                    new ListShipmentsQuery(customer, ShipmentStatus.CREATED, new PageQuery(1, 1)));

            assertThat(cancelled.items()).extracting(ShipmentView::id).containsExactly(first.id());
            assertThat(secondPage.items()).hasSize(1);
            assertThat(secondPage.totalItems()).isEqualTo(2);
            assertThat(secondPage.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("consultas usam transação somente leitura")
        void queriesAreReadOnly() {
            ShipmentView created = createSpToRio();
            var readOnlyObserver = new DirectTransactionRunner() {
                boolean sawReadOnly;

                @Override
                public <T> T inReadOnlyTransaction(java.util.function.Supplier<T> work) {
                    sawReadOnly = true;
                    return super.inReadOnlyTransaction(work);
                }
            };

            new GetShipmentService(shipments, readOnlyObserver).get(customer, created.id());

            assertThat(readOnlyObserver.sawReadOnly).isTrue();
        }
    }

    @Nested
    @DisplayName("histórico de rastreamento")
    class Tracking {

        @Test
        @DisplayName("projeção é idempotente: o mesmo evento duas vezes gera uma entrada")
        void recordIsIdempotent() {
            ShipmentView created = createSpToRio();
            var command = new RecordTrackingEventCommand(UUID.randomUUID(), created.id(), ShipmentStatus.IN_TRANSIT,
                    NOW.plusSeconds(10), Map.of());

            assertThat(record.record(command)).isTrue();
            assertThat(record.record(command)).isFalse();
            assertThat(tracking.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("histórico sai em ordem cronológica, mesmo se os eventos chegarem fora de ordem")
        void historyIsChronological() {
            ShipmentView created = createSpToRio();
            record.record(new RecordTrackingEventCommand(UUID.randomUUID(), created.id(), ShipmentStatus.IN_TRANSIT,
                    NOW.plusSeconds(60), Map.of()));
            record.record(new RecordTrackingEventCommand(UUID.randomUUID(), created.id(), ShipmentStatus.CREATED,
                    NOW, Map.of("originCity", "São Paulo", "originState", "SP",
                    "destinationCity", "Rio de Janeiro", "destinationState", "RJ")));

            List<TrackingEntryView> entries = history.history(customer, created.id());

            assertThat(entries).extracting(TrackingEntryView::status)
                    .containsExactly(ShipmentStatus.CREATED, ShipmentStatus.IN_TRANSIT);
            assertThat(entries.getFirst().description()).isEqualTo("Envio registrado: São Paulo/SP → Rio de Janeiro/RJ");
            assertThat(entries.getLast().description()).isEqualTo("Objeto em trânsito");
        }

        @Test
        @DisplayName("descrições cobrem todos os status e toleram dados ausentes")
        void descriptions() {
            UUID shipmentId = UUID.randomUUID();
            record.record(new RecordTrackingEventCommand(UUID.randomUUID(), shipmentId, ShipmentStatus.CREATED,
                    NOW, Map.of()));
            record.record(new RecordTrackingEventCommand(UUID.randomUUID(), shipmentId, ShipmentStatus.DELIVERED,
                    NOW.plusSeconds(1), Map.of()));
            record.record(new RecordTrackingEventCommand(UUID.randomUUID(), shipmentId, ShipmentStatus.CANCELLED,
                    NOW.plusSeconds(2), Map.of("reason", "Desistência")));
            record.record(new RecordTrackingEventCommand(UUID.randomUUID(), shipmentId, ShipmentStatus.CANCELLED,
                    NOW.plusSeconds(3), null));

            assertThat(tracking.findByShipmentId(ShipmentId.of(shipmentId)))
                    .extracting(TrackingEntry::description)
                    .containsExactly(
                            "Envio registrado: ?/? → ?/?",
                            "Objeto entregue ao destinatário",
                            "Envio cancelado: Desistência",
                            "Envio cancelado: motivo não informado");
            assertThat(tracking.findByShipmentId(ShipmentId.of(shipmentId)))
                    .allSatisfy(entry -> assertThat(entry.recordedAt()).isEqualTo(NOW));
        }

        @Test
        @DisplayName("histórico de envio alheio é 404, verificado na fonte da verdade")
        void historyRespectsVisibility() {
            ShipmentView created = createSpToRio();

            assertThatThrownBy(() -> history.history(otherCustomer, created.id()))
                    .isInstanceOf(ShipmentNotFoundException.class);
            assertThat(history.history(operator, created.id())).isEmpty();
        }
    }
}
