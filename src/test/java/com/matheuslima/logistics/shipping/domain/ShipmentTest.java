package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;
import com.matheuslima.logistics.shipping.ShippingFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.matheuslima.logistics.shipping.ShippingFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do agregado {@link Shipment}: invariantes, transições e eventos emitidos.
 *
 * <p>São testes unitários puros: sem Spring, sem banco, sem mocks. Rodam em milissegundos e
 * formam a base da pirâmide de testes. Se uma regra de negócio quebrar, é aqui que
 * descobrimos primeiro.
 *
 * <p>{@link Nested} agrupa os cenários por comportamento; o relatório de testes fica legível
 * como uma especificação ("Shipment > cancelamento > rejeita motivo em branco").
 */
@DisplayName("Shipment (agregado)")
class ShipmentTest {

    private final CustomerId owner = ShippingFixtures.aCustomer();
    private final Instant later = NOW.plusSeconds(3600);

    @Nested
    @DisplayName("criação")
    class Creation {

        @Test
        @DisplayName("nasce como CREATED, com timestamps iguais e um evento Created")
        void createsShipmentInCreatedStatus() {
            Shipment shipment = Shipment.create(owner, ShippingFixtures.saoPauloSe(), ShippingFixtures.rioDeJaneiro(),
                    Weight.ofGrams(1_500), Money.brl("30.00"), NOW);

            assertThat(shipment.status()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(shipment.createdAt()).isEqualTo(NOW);
            assertThat(shipment.updatedAt()).isEqualTo(NOW);
            assertThat(shipment.cancellationReason()).isNull();
            assertThat(shipment.belongsTo(owner)).isTrue();

            assertThat(shipment.pullDomainEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(ShipmentEvent.Created.class, event -> {
                        assertThat(event.shipmentId()).isEqualTo(shipment.id());
                        assertThat(event.customerId()).isEqualTo(owner);
                        assertThat(event.freight()).isEqualTo(Money.brl("30.00"));
                        assertThat(event.occurredAt()).isEqualTo(NOW);
                        assertThat(event.resultingStatus()).isEqualTo(ShipmentStatus.CREATED);
                    });
        }

        @Test
        @DisplayName("pullDomainEvents esvazia a lista para evitar publicação duplicada")
        void pullingEventsClearsThem() {
            Shipment shipment = Shipment.create(owner, ShippingFixtures.saoPauloSe(), ShippingFixtures.rioDeJaneiro(),
                    Weight.ofGrams(1_500), Money.brl("30.00"), NOW);

            assertThat(shipment.pullDomainEvents()).hasSize(1);
            assertThat(shipment.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("rejeita frete negativo (erro de programação, não de entrada)")
        void rejectsNegativeFreight() {
            assertThatThrownBy(() -> Shipment.create(owner, ShippingFixtures.saoPauloSe(),
                    ShippingFixtures.rioDeJaneiro(), Weight.ofGrams(1_000), Money.brl("-1.00"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("fluxo feliz: despacho e entrega")
    class HappyPath {

        @Test
        @DisplayName("CREATED -> IN_TRANSIT -> DELIVERED emitindo um evento por transição")
        void dispatchesAndDelivers() {
            Shipment shipment = ShippingFixtures.newShipment(owner);

            shipment.dispatch(later);
            assertThat(shipment.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
            assertThat(shipment.updatedAt()).isEqualTo(later);

            shipment.deliver(later.plusSeconds(60));
            assertThat(shipment.status()).isEqualTo(ShipmentStatus.DELIVERED);

            assertThat(shipment.pullDomainEvents())
                    .extracting(ShipmentEvent::resultingStatus)
                    .containsExactly(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED);
        }
    }

    @Nested
    @DisplayName("cancelamento")
    class Cancellation {

        @Test
        @DisplayName("cancela envio CREATED guardando o motivo sem espaços nas pontas")
        void cancelsCreatedShipment() {
            Shipment shipment = ShippingFixtures.newShipment(owner);

            shipment.cancel("  Cliente desistiu  ", later);

            assertThat(shipment.status()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(shipment.cancellationReason()).isEqualTo("Cliente desistiu");
            assertThat(shipment.pullDomainEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(ShipmentEvent.Cancelled.class,
                            event -> assertThat(event.reason()).isEqualTo("Cliente desistiu"));
        }

        @Test
        @DisplayName("rejeita motivo em branco")
        void rejectsBlankReason() {
            Shipment shipment = ShippingFixtures.newShipment(owner);

            assertThatThrownBy(() -> shipment.cancel("   ", later))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("obrigatório");
            assertThat(shipment.status()).isEqualTo(ShipmentStatus.CREATED);
        }

        @Test
        @DisplayName("rejeita motivo maior que o limite")
        void rejectsTooLongReason() {
            Shipment shipment = ShippingFixtures.newShipment(owner);
            String tooLong = "x".repeat(Shipment.MAX_CANCELLATION_REASON_LENGTH + 1);

            assertThatThrownBy(() -> shipment.cancel(tooLong, later))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("não permite cancelar depois do despacho (409)")
        void cannotCancelInTransit() {
            Shipment shipment = ShippingFixtures.newShipment(owner);
            shipment.dispatch(later);

            assertThatThrownBy(() -> shipment.cancel("Mudei de ideia", later))
                    .isInstanceOf(InvalidShipmentStatusTransitionException.class)
                    .hasMessageContaining("IN_TRANSIT")
                    .hasMessageContaining("CANCELLED");
            assertThat(shipment.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
            assertThat(shipment.cancellationReason()).isNull();
        }
    }

    @Nested
    @DisplayName("transições inválidas")
    class InvalidTransitions {

        @Test
        @DisplayName("não entrega um envio que não foi despachado")
        void cannotDeliverBeforeDispatch() {
            Shipment shipment = ShippingFixtures.newShipment(owner);

            assertThatThrownBy(() -> shipment.deliver(later))
                    .isInstanceOf(InvalidShipmentStatusTransitionException.class);
            assertThat(shipment.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("não despacha duas vezes")
        void cannotDispatchTwice() {
            Shipment shipment = ShippingFixtures.newShipment(owner);
            shipment.dispatch(later);

            assertThatThrownBy(() -> shipment.dispatch(later))
                    .isInstanceOf(InvalidShipmentStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("reconstituição e identidade")
    class RestoreAndIdentity {

        @Test
        @DisplayName("restore não emite eventos e preserva todos os campos")
        void restoreDoesNotEmitEvents() {
            ShipmentId id = ShipmentId.newId();
            Shipment restored = Shipment.restore(id, owner, ShippingFixtures.saoPauloSe(),
                    ShippingFixtures.rioDeJaneiro(), Weight.ofGrams(1_500), Money.brl("30.00"),
                    ShipmentStatus.CANCELLED, "Endereço errado", NOW, later);

            assertThat(restored.pullDomainEvents()).isEmpty();
            assertThat(restored.id()).isEqualTo(id);
            assertThat(restored.status()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(restored.cancellationReason()).isEqualTo("Endereço errado");
            assertThat(restored.updatedAt()).isEqualTo(later);
            assertThat(restored.origin().city()).isEqualTo("São Paulo");
            assertThat(restored.destination().state()).isEqualTo(BrazilianState.RJ);
            assertThat(restored.weight().grams()).isEqualTo(1_500);
            assertThat(restored).hasToString("Shipment[id=" + id + ", status=CANCELLED]");
        }

        @Test
        @DisplayName("igualdade é pela identidade, não pelos atributos")
        void equalityByIdentity() {
            ShipmentId id = ShipmentId.newId();
            Shipment a = Shipment.restore(id, owner, ShippingFixtures.saoPauloSe(), ShippingFixtures.rioDeJaneiro(),
                    Weight.ofGrams(1_000), Money.brl("10.00"), ShipmentStatus.CREATED, null, NOW, NOW);
            Shipment sameIdDifferentState = Shipment.restore(id, owner, ShippingFixtures.saoPauloSe(),
                    ShippingFixtures.manaus(), Weight.ofGrams(2_000), Money.brl("99.00"),
                    ShipmentStatus.DELIVERED, null, NOW, later);

            assertThat(a).isEqualTo(sameIdDifferentState).hasSameHashCodeAs(sameIdDifferentState);
            assertThat(a).isNotEqualTo(ShippingFixtures.newShipment(owner));
        }

        @Test
        @DisplayName("belongsTo distingue o dono de outros clientes")
        void belongsToOwnerOnly() {
            Shipment shipment = ShippingFixtures.newShipment(owner);

            assertThat(shipment.belongsTo(owner)).isTrue();
            assertThat(shipment.belongsTo(ShippingFixtures.aCustomer())).isFalse();
        }
    }
}
