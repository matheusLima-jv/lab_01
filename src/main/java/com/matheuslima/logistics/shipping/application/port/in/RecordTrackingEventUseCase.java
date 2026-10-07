package com.matheuslima.logistics.shipping.application.port.in;

/**
 * <b>Porta de entrada</b>: registrar no histórico de rastreamento um evento recebido do broker.
 *
 * <p>Sim, isto é uma porta de <i>entrada</i>, mesmo sendo chamada por uma fila: na arquitetura
 * hexagonal, "entrada" (driving) é tudo que <b>dispara</b> o núcleo, seja HTTP, mensagem, CLI ou
 * agendamento. O listener do RabbitMQ é um adaptador de entrada tanto quanto um controller.
 */
public interface RecordTrackingEventUseCase {

    /**
     * Registra o evento de forma <b>idempotente</b>.
     *
     * @return {@code true} se o evento foi registrado agora; {@code false} se ele já existia (mensagem
     *         duplicada, situação normal em entrega "pelo menos uma vez")
     */
    boolean record(RecordTrackingEventCommand command);
}
