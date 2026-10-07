package com.matheuslima.logistics.shared.infrastructure.outbox;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Envia uma {@link OutboxMessage} ao RabbitMQ e só retorna depois que o broker <b>confirma</b>
 * o recebimento.
 *
 * <h2>Publisher confirms: por que esperar a confirmação?</h2>
 * {@code rabbitTemplate.send(...)} sem confirmação é "dispara e esquece": o método retorna
 * assim que os bytes saem do processo. Se o broker cair nesse instante, a mensagem se perde, e o
 * relay já a teria marcado como publicada. Com <i>publisher confirms</i>
 * ({@code spring.rabbitmq.publisher-confirm-type: correlated}), o broker responde ack depois de
 * gravar a mensagem (em disco, para mensagens persistentes em filas duráveis). Só então
 * marcamos a linha do outbox como publicada.
 *
 * <p>O que NÃO é verdade: "ack do broker = mensagem consumida". O ack só garante que o broker
 * assumiu a responsabilidade pela mensagem. Se nenhuma fila estiver ligada à routing key, o
 * broker aceita e descarta (a menos que se use {@code mandatory} ou um alternate exchange).
 *
 * <h2>Propriedades da mensagem</h2>
 * <ul>
 *   <li>{@code messageId} = ID do evento: chave de idempotência para o consumidor.</li>
 *   <li>{@code contentType=application/json}: o conversor do consumidor usa isso para saber
 *       como desserializar.</li>
 *   <li>{@code deliveryMode=PERSISTENT}: a mensagem sobrevive a um restart do broker.</li>
 * </ul>
 */
@Component
public class RabbitOutboxPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitOutboxPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = properties.confirmTimeout();
    }

    /**
     * @throws OutboxPublishException se o broker não confirmar dentro do timeout, rejeitar a
     *         mensagem (nack) ou estiver inacessível
     */
    public void publish(OutboxMessage outboxMessage) {
        Message message = MessageBuilder
                .withBody(outboxMessage.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(outboxMessage.getId().toString())
                .setType(outboxMessage.getRoutingKey())
                .setTimestamp(Date.from(outboxMessage.getOccurredAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("x-aggregate-type", outboxMessage.getAggregateType())
                .setHeader("x-aggregate-id", outboxMessage.getAggregateId().toString())
                .build();

        CorrelationData correlation = new CorrelationData(outboxMessage.getId().toString());
        try {
            rabbitTemplate.send(outboxMessage.getExchange(), outboxMessage.getRoutingKey(), message, correlation);
        } catch (RuntimeException brokerUnavailable) {
            throw new OutboxPublishException("Could not send message to broker", brokerUnavailable);
        }
        awaitConfirmation(correlation);
    }

    private void awaitConfirmation(CorrelationData correlation) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new OutboxPublishException("Broker rejected message (nack): " + confirm.reason());
            }
        } catch (InterruptedException interrupted) {
            // Regra de ouro: ao capturar InterruptedException, restaure a flag de interrupção.
            // Engolir a interrupção impede um shutdown limpo da thread.
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Interrupted while waiting for broker confirmation", interrupted);
        } catch (ExecutionException | TimeoutException noConfirmation) {
            throw new OutboxPublishException("No broker confirmation within " + confirmTimeout, noConfirmation);
        }
    }
}
