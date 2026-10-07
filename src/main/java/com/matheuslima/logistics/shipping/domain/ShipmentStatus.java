package com.matheuslima.logistics.shipping.domain;

/**
 * Ciclo de vida de um envio, modelado como <b>máquina de estados</b>.
 *
 * <pre>
 *   CREATED ──dispatch──► IN_TRANSIT ──deliver──► DELIVERED
 *      │
 *      └──cancel──► CANCELLED
 * </pre>
 *
 * <h2>Por que as transições ficam no enum?</h2>
 * Concentrar "quem pode ir para onde" em um único {@code switch} torna a regra visível,
 * testável em isolamento ({@code ShipmentStatusTest}) e impossível de esquecer: o
 * {@code switch} sobre o próprio enum é <i>exaustivo</i>, então adicionar um novo status sem
 * definir suas transições é erro de compilação.
 *
 * <h2>Regra de negócio: por que não dá para cancelar em trânsito?</h2>
 * Depois de despachado, o pacote está fisicamente na rede de transporte. Interromper isso é
 * um processo diferente (devolução/logística reversa), com custo e fluxo próprios, fora do
 * escopo deste projeto. O modelo deixa isso explícito em vez de permitir um cancelamento que a
 * operação não consegue cumprir.
 */
public enum ShipmentStatus {

    /** Registrado e aguardando coleta. Único estado em que o cliente pode cancelar. */
    CREATED,
    /** Coletado e em transporte. */
    IN_TRANSIT,
    /** Entregue ao destinatário. Estado final. */
    DELIVERED,
    /** Cancelado antes do despacho. Estado final. */
    CANCELLED;

    /**
     * @param target estado desejado
     * @return {@code true} se a transição do estado atual para {@code target} é permitida
     */
    public boolean canTransitionTo(ShipmentStatus target) {
        return switch (this) {
            case CREATED -> target == IN_TRANSIT || target == CANCELLED;
            case IN_TRANSIT -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /** Estados finais não admitem nenhuma transição. */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
