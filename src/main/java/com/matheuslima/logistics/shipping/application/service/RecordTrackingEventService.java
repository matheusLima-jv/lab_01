package com.matheuslima.logistics.shipping.application.service;

import com.matheuslima.logistics.shipping.application.port.in.RecordTrackingEventCommand;
import com.matheuslima.logistics.shipping.application.port.in.RecordTrackingEventUseCase;
import com.matheuslima.logistics.shipping.application.port.out.TrackingEntry;
import com.matheuslima.logistics.shipping.application.port.out.TrackingHistoryStore;
import com.matheuslima.logistics.shipping.domain.ShipmentId;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * Caso de uso: projetar um evento de envio no histórico de rastreamento.
 *
 * <h2>Idempotência: a propriedade mais importante de um consumidor</h2>
 * Com entrega "pelo menos uma vez", a mesma mensagem pode chegar duas vezes (por exemplo, a
 * aplicação cai depois de gravar no Mongo e antes de confirmar a mensagem ao RabbitMQ, que
 * então a reentrega). Processar duas vezes duplicaria a linha do tempo. A solução: o
 * {@code eventId} é a chave da entrada no armazenamento, e uma segunda gravação com a mesma chave
 * é simplesmente ignorada. "Pelo menos uma vez" + idempotência resulta, na prática, em efeito
 * exatamente uma vez.
 *
 * <h2>Por que sem transação?</h2>
 * A gravação é um único documento no MongoDB, operação atômica por natureza. Não há duas escritas
 * para manter consistentes entre si.
 */
public final class RecordTrackingEventService implements RecordTrackingEventUseCase {

    private final TrackingHistoryStore trackingHistory;
    private final Clock clock;

    public RecordTrackingEventService(TrackingHistoryStore trackingHistory, Clock clock) {
        this.trackingHistory = Objects.requireNonNull(trackingHistory);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public boolean record(RecordTrackingEventCommand command) {
        TrackingEntry entry = new TrackingEntry(
                command.eventId(),
                ShipmentId.of(command.shipmentId()),
                command.status(),
                describe(command),
                command.occurredAt(),
                command.details(),
                clock.instant());
        return trackingHistory.append(entry);
    }

    /**
     * Texto exibido ao cliente final. O {@code switch} sobre o enum é exaustivo: um novo status
     * sem descrição não compila.
     *
     * <p>Os valores de {@code details} podem faltar se um produtor antigo enviar uma mensagem
     * com menos campos; por isso usamos {@code getOrDefault} em vez de supor que o dado existe.
     * Consumidores tolerantes ("seja liberal no que aceita") sobrevivem à evolução dos contratos.
     */
    static String describe(RecordTrackingEventCommand command) {
        Map<String, String> details = command.details();
        return switch (command.status()) {
            case CREATED -> "Envio registrado: %s/%s → %s/%s".formatted(
                    details.getOrDefault("originCity", "?"), details.getOrDefault("originState", "?"),
                    details.getOrDefault("destinationCity", "?"), details.getOrDefault("destinationState", "?"));
            case IN_TRANSIT -> "Objeto em trânsito";
            case DELIVERED -> "Objeto entregue ao destinatário";
            case CANCELLED -> "Envio cancelado: " + details.getOrDefault("reason", "motivo não informado");
        };
    }
}
