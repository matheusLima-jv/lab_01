package com.matheuslima.logistics.shipping.adapter.in.messaging;

import com.matheuslima.logistics.shipping.adapter.messaging.ShipmentEventMessage;
import com.matheuslima.logistics.shipping.application.port.in.RecordTrackingEventCommand;
import com.matheuslima.logistics.shipping.application.port.in.RecordTrackingEventUseCase;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * <b>Adaptador de entrada</b> por mensageria: consome os eventos de envio e alimenta o histórico
 * de rastreamento (projeção CQRS no MongoDB).
 *
 * <h2>Ciclo de vida de uma mensagem</h2>
 * <ol>
 *   <li>O broker entrega a mensagem; o {@code JacksonJsonMessageConverter} converte o JSON em
 *       {@link ShipmentEventMessage} (o tipo é inferido do parâmetro do método).</li>
 *   <li>O listener traduz o contrato de integração em comando do caso de uso.</li>
 *   <li>Se o método termina sem exceção, o Spring confirma a mensagem (ack) e o broker a apaga.</li>
 *   <li>Se lança exceção, o Spring tenta de novo, em memória, com backoff (configuração
 *       {@code spring.rabbitmq.listener.simple.retry}). Esgotadas as tentativas, rejeita sem
 *       reenfileirar, e a fila manda a mensagem para a DLQ.</li>
 * </ol>
 *
 * <h2>Duplicatas são normais, e por isso tratadas</h2>
 * A entrega é "pelo menos uma vez". O caso de uso é idempotente e devolve {@code false} quando o
 * evento já tinha sido registrado. Isso não é erro: a mensagem é confirmada e só registramos
 * no log em nível DEBUG.
 *
 * <h2>Mensagem inválida (ex.: status desconhecido)</h2>
 * {@code ShipmentStatus.valueOf} lança exceção para um status que esta versão não conhece. Ela
 * percorre as retentativas e termina na DLQ, onde fica disponível para análise, em vez de sumir
 * ou travar a fila.
 */
@Component
public class TrackingProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(TrackingProjectionListener.class);

    private final RecordTrackingEventUseCase recordTrackingEvent;

    public TrackingProjectionListener(RecordTrackingEventUseCase recordTrackingEvent) {
        this.recordTrackingEvent = recordTrackingEvent;
    }

    /**
     * O nome da fila vem da configuração via placeholder. O {@code id} identifica o container do
     * listener (útil em métricas e para parar/iniciar o consumo programaticamente).
     */
    @RabbitListener(id = "tracking-projection", queues = "${app.messaging.shipping.tracking-queue}")
    public void on(ShipmentEventMessage message) {
        boolean recorded = recordTrackingEvent.record(new RecordTrackingEventCommand(
                message.eventId(),
                message.shipmentId(),
                ShipmentStatus.valueOf(message.status()),
                message.occurredAt(),
                withoutNulls(message.details())));

        if (recorded) {
            log.debug("Tracking event {} ({}) recorded for shipment {}",
                    message.eventId(), message.eventType(), message.shipmentId());
        } else {
            log.debug("Duplicate tracking event {} ignored (at-least-once delivery)", message.eventId());
        }
    }

    /** O contrato aceita valores nulos em {@code details}; o núcleo usa mapas imutáveis sem nulos. */
    private static Map<String, String> withoutNulls(Map<String, String> details) {
        Map<String, String> clean = new HashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (key != null && value != null) {
                    clean.put(key, value);
                }
            });
        }
        return clean;
    }
}
