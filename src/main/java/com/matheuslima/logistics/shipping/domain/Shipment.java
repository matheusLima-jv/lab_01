package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.AggregateRoot;
import com.matheuslima.logistics.shared.domain.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Envio de uma encomenda: <b>raiz de agregado</b> do contexto de logística.
 *
 * <h2>Modelo rico versus modelo anêmico</h2>
 * Em um modelo <i>anêmico</i>, a entidade é só um saco de getters e setters e as regras ficam
 * espalhadas em "services" ({@code if (s.getStatus() == ...) s.setStatus(...)}). Aqui as regras
 * ficam <b>no próprio agregado</b>: não existe {@code setStatus}; existem {@link #dispatch},
 * {@link #deliver} e {@link #cancel}, que validam a transição, atualizam o estado e registram o
 * evento correspondente em uma única operação. É impossível mudar o status "esquecendo" de
 * emitir o evento ou de validar a transição.
 *
 * <h2>Invariantes garantidas por esta classe</h2>
 * <ul>
 *   <li>O status só muda por transições válidas ({@link ShipmentStatus#canTransitionTo}).</li>
 *   <li>Todo envio cancelado tem motivo, e só envios cancelados têm motivo.</li>
 *   <li>Toda mudança de estado gera exatamente um evento de domínio.</li>
 *   <li>Endereços, peso, frete e dono são imutáveis após a criação.</li>
 * </ul>
 *
 * <h2>Por que o frete é recebido pronto em {@link #create}?</h2>
 * O cálculo depende de uma tabela de preços configurável ({@code FreightCalculator}, um
 * <i>domain service</i>). O caso de uso orquestra: obtém os endereços, pede o frete ao
 * calculador e cria o envio. O agregado não precisa saber <i>como</i> o preço é composto,
 * apenas guardá-lo. Isso mantém o agregado pequeno e o cálculo testável em isolamento.
 *
 * <h2>E concorrência (dois operadores despachando ao mesmo tempo)?</h2>
 * O agregado não sabe nada disso, de propósito. O adaptador JPA usa <b>bloqueio otimista</b>
 * ({@code @Version}): se duas transações carregarem a mesma versão, a segunda a gravar falha e a
 * API responde 409. O domínio cuida das regras; a infraestrutura cuida da concorrência.
 */
public final class Shipment extends AggregateRoot<ShipmentEvent> {

    public static final int MAX_CANCELLATION_REASON_LENGTH = 255;

    private final ShipmentId id;
    private final CustomerId customerId;
    private final Address origin;
    private final Address destination;
    private final Weight weight;
    private final Money freight;
    private final Instant createdAt;
    private ShipmentStatus status;
    private String cancellationReason;
    private Instant updatedAt;

    // Construtor privado com muitos parâmetros: aceitável porque só as fábricas create/restore o
    // chamam. Para quem usa a classe, a API pública são as fábricas com nomes de intenção.
    private Shipment(ShipmentId id, CustomerId customerId, Address origin, Address destination, Weight weight,
                     Money freight, ShipmentStatus status, String cancellationReason, Instant createdAt,
                     Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
        this.destination = Objects.requireNonNull(destination, "destination must not be null");
        this.weight = Objects.requireNonNull(weight, "weight must not be null");
        this.freight = Objects.requireNonNull(freight, "freight must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.cancellationReason = cancellationReason;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (freight.isNegative()) {
            throw new IllegalArgumentException("freight must not be negative");
        }
    }

    /**
     * Registra um novo envio no estado {@link ShipmentStatus#CREATED} e emite
     * {@link ShipmentEvent.Created}.
     *
     * @param now instante atual vindo de um {@code Clock} injetado no caso de uso. O domínio
     *            nunca chama {@code Instant.now()} direto, o que torna os testes determinísticos.
     */
    public static Shipment create(CustomerId customerId, Address origin, Address destination, Weight weight,
                                  Money freight, Instant now) {
        Shipment shipment = new Shipment(ShipmentId.newId(), customerId, origin, destination, weight, freight,
                ShipmentStatus.CREATED, null, now, now);
        shipment.registerEvent(new ShipmentEvent.Created(
                UUID.randomUUID(), now, shipment.id, customerId, origin, destination, weight, freight));
        return shipment;
    }

    /**
     * Reconstitui um envio a partir do armazenamento, sem emitir eventos (nada "aconteceu";
     * estamos apenas lendo o que já aconteceu). Uso exclusivo de adaptadores de persistência.
     */
    public static Shipment restore(ShipmentId id, CustomerId customerId, Address origin, Address destination,
                                   Weight weight, Money freight, ShipmentStatus status, String cancellationReason,
                                   Instant createdAt, Instant updatedAt) {
        return new Shipment(id, customerId, origin, destination, weight, freight, status, cancellationReason,
                createdAt, updatedAt);
    }

    /** O pacote foi coletado: {@code CREATED -> IN_TRANSIT}. */
    public void dispatch(Instant now) {
        transitionTo(ShipmentStatus.IN_TRANSIT, now);
        registerEvent(new ShipmentEvent.Dispatched(UUID.randomUUID(), now, id, customerId));
    }

    /** O pacote chegou ao destinatário: {@code IN_TRANSIT -> DELIVERED}. */
    public void deliver(Instant now) {
        transitionTo(ShipmentStatus.DELIVERED, now);
        registerEvent(new ShipmentEvent.Delivered(UUID.randomUUID(), now, id, customerId));
    }

    /**
     * Cancela o envio: {@code CREATED -> CANCELLED}.
     *
     * @param reason motivo obrigatório. Cancelamentos sem motivo dificultam o atendimento
     *               e a análise de causas (ex.: muitos cancelamentos por "frete caro").
     */
    public void cancel(String reason, Instant now) {
        String validReason = validateCancellationReason(reason);
        transitionTo(ShipmentStatus.CANCELLED, now);
        this.cancellationReason = validReason;
        registerEvent(new ShipmentEvent.Cancelled(UUID.randomUUID(), now, id, customerId, validReason));
    }

    /** Pertence ao cliente informado? Base da regra de visibilidade "cada um vê o seu". */
    public boolean belongsTo(CustomerId candidate) {
        return customerId.equals(candidate);
    }

    private void transitionTo(ShipmentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidShipmentStatusTransitionException(id, status, target);
        }
        this.status = target;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String validateCancellationReason(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        String stripped = reason.strip();
        if (stripped.isEmpty()) {
            throw new DomainValidationException("INVALID_CANCELLATION_REASON", "O motivo do cancelamento é obrigatório.");
        }
        if (stripped.length() > MAX_CANCELLATION_REASON_LENGTH) {
            throw new DomainValidationException("INVALID_CANCELLATION_REASON",
                    "O motivo do cancelamento deve ter no máximo %d caracteres."
                            .formatted(MAX_CANCELLATION_REASON_LENGTH));
        }
        return stripped;
    }

    public ShipmentId id() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public Address origin() {
        return origin;
    }

    public Address destination() {
        return destination;
    }

    public Weight weight() {
        return weight;
    }

    public Money freight() {
        return freight;
    }

    public ShipmentStatus status() {
        return status;
    }

    /** Motivo do cancelamento, ou {@code null} se o envio não foi cancelado. */
    public String cancellationReason() {
        return cancellationReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Igualdade por identidade (é uma entidade, não um Value Object). */
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Shipment shipment && id.equals(shipment.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Shipment[id=" + id + ", status=" + status + "]";
    }
}
