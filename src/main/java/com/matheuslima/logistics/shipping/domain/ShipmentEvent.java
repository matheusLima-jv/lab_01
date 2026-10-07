package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Catálogo completo dos eventos de domínio de um envio.
 *
 * <h2>Por que uma interface {@code sealed}?</h2>
 * {@code sealed} (Java 17+) fecha a hierarquia: só os records declarados aqui podem implementar
 * {@code ShipmentEvent}. Com isso, um {@code switch} sobre um {@code ShipmentEvent} pode ser
 * <b>exaustivo sem {@code default}</b>, e o compilador verifica que todos os casos foram
 * tratados. Quando alguém criar um novo evento (ex.: {@code Returned}), todo {@code switch} que
 * o ignore deixa de compilar (por exemplo, o mapeamento para mensagens do RabbitMQ). É o
 * compilador lembrando você de atualizar os consumidores.
 *
 * <h2>Por que records aninhados num único arquivo?</h2>
 * Quem quer saber "o que pode acontecer com um envio" lê um único arquivo. E o nome fica
 * expressivo no uso: {@code ShipmentEvent.Cancelled}.
 *
 * <h2>Por que um evento por transição (e não um genérico "StatusChanged")?</h2>
 * Eventos específicos carregam dados específicos (o cancelamento tem motivo, a criação tem
 * endereços) e falam a linguagem do negócio. Um evento genérico com campo {@code status}
 * obrigaria todo consumidor a inspecionar o conteúdo para descobrir o que aconteceu.
 *
 * <h2>Por que todo evento carrega {@code customerId}?</h2>
 * Consumidores (ex.: um futuro serviço de notificação) precisam saber de quem é o envio sem
 * consultar a base de envios. Carregar no evento o estado necessário (<i>event-carried state
 * transfer</i>) evita acoplamento temporal: o consumidor funciona mesmo que a API esteja fora.
 */
public sealed interface ShipmentEvent extends DomainEvent {

    ShipmentId shipmentId();

    CustomerId customerId();

    /** Status do envio imediatamente após o fato. */
    ShipmentStatus resultingStatus();

    /** O envio foi registrado e o frete calculado. */
    record Created(
            UUID eventId,
            Instant occurredAt,
            ShipmentId shipmentId,
            CustomerId customerId,
            Address origin,
            Address destination,
            Weight weight,
            Money freight) implements ShipmentEvent {

        @Override
        public ShipmentStatus resultingStatus() {
            return ShipmentStatus.CREATED;
        }
    }

    /** O pacote foi coletado e está em trânsito. */
    record Dispatched(UUID eventId, Instant occurredAt, ShipmentId shipmentId, CustomerId customerId)
            implements ShipmentEvent {

        @Override
        public ShipmentStatus resultingStatus() {
            return ShipmentStatus.IN_TRANSIT;
        }
    }

    /** O pacote foi entregue ao destinatário. */
    record Delivered(UUID eventId, Instant occurredAt, ShipmentId shipmentId, CustomerId customerId)
            implements ShipmentEvent {

        @Override
        public ShipmentStatus resultingStatus() {
            return ShipmentStatus.DELIVERED;
        }
    }

    /** O envio foi cancelado antes do despacho. */
    record Cancelled(UUID eventId, Instant occurredAt, ShipmentId shipmentId, CustomerId customerId, String reason)
            implements ShipmentEvent {

        @Override
        public ShipmentStatus resultingStatus() {
            return ShipmentStatus.CANCELLED;
        }
    }
}
