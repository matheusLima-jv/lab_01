package com.matheuslima.logistics.shipping.config;

import com.matheuslima.logistics.shipping.adapter.messaging.ShippingMessagingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do RabbitMQ do contexto de envios, declarada como <b>beans</b>.
 *
 * <pre>
 *                       routing key "shipment.*"
 *  [logistics.shipping.events] ──────────────────► (logistics.shipping.tracking) ──► listener
 *         (topic exchange)                                │  rejeitada após retries
 *                                                         ▼
 *                                      [logistics.shipping.events.dlx] ──► (logistics.shipping.tracking.dlq)
 *                                             (direct exchange)                  (análise manual)
 * </pre>
 *
 * <h2>Como beans viram filas de verdade</h2>
 * O Spring Boot cria um {@code RabbitAdmin}, que, ao abrir a primeira conexão, encontra todos os
 * beans {@link org.springframework.amqp.core.Declarable} (exchanges, filas, bindings) e os
 * declara no broker. A declaração é idempotente: se já existirem com os mesmos argumentos, nada
 * muda. Infraestrutura como código, versionada junto com a aplicação.
 *
 * <h2>Por que exchange do tipo <i>topic</i>?</h2>
 * O produtor publica em um exchange, nunca em uma fila. Com topic, cada consumidor escolhe o que
 * quer por padrão de routing key: a projeção quer {@code shipment.*} (tudo); um futuro serviço de
 * notificação poderia querer só {@code shipment.delivered}. Novos consumidores entram sem nenhuma
 * mudança no produtor (pub/sub, fan-out por interesse).
 *
 * <h2>Por que fila <i>quorum</i>?</h2>
 * É o tipo de fila durável recomendado pelo RabbitMQ desde a 3.8 (e o espelhamento de filas
 * clássicas foi removido na 4.0). A fila é replicada via Raft entre os nós do cluster: perder um
 * nó não perde mensagens. Em um único nó (ambiente local) funciona igual, então o mesmo código
 * vale para produção.
 *
 * <h2>Dead Letter Exchange (DLX): o que fazer com mensagens "venenosas"</h2>
 * Uma mensagem que falha sempre (JSON inválido, bug no consumidor) não pode ficar voltando para
 * a fila para sempre, travando o consumo e gastando CPU. Depois de esgotar as retentativas, o
 * listener a rejeita sem reenfileirar; a fila a encaminha ao DLX, que a entrega na DLQ. Lá ela
 * fica para análise e, depois da correção, pode ser reprocessada (reenviada à fila original pelo
 * painel do RabbitMQ ou por uma ferramenta).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShippingMessagingProperties.class)
public class ShippingMessagingConfig {

    /** Padrão de binding: todos os eventos de envio ({@code *} casa exatamente uma palavra). */
    static final String ALL_SHIPMENT_EVENTS = "shipment.*";

    @Bean
    TopicExchange shippingEventsExchange(ShippingMessagingProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    @Bean
    DirectExchange shippingDeadLetterExchange(ShippingMessagingProperties properties) {
        return ExchangeBuilder.directExchange(properties.deadLetterExchange()).durable(true).build();
    }

    /**
     * Fila da projeção de rastreamento. Os argumentos {@code x-dead-letter-*} dizem ao broker para
     * onde mandar as mensagens rejeitadas. A routing key de dead-letter é o nome da própria fila,
     * o que permite usar um único DLX para várias filas, cada uma com sua DLQ.
     */
    @Bean
    Queue trackingQueue(ShippingMessagingProperties properties) {
        return QueueBuilder.durable(properties.trackingQueue())
                .quorum()
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(properties.trackingQueue())
                .build();
    }

    @Bean
    Queue trackingDeadLetterQueue(ShippingMessagingProperties properties) {
        return QueueBuilder.durable(properties.trackingDeadLetterQueue()).quorum().build();
    }

    @Bean
    Binding trackingQueueBinding(Queue trackingQueue, TopicExchange shippingEventsExchange) {
        return BindingBuilder.bind(trackingQueue).to(shippingEventsExchange).with(ALL_SHIPMENT_EVENTS);
    }

    @Bean
    Binding trackingDeadLetterBinding(Queue trackingDeadLetterQueue, DirectExchange shippingDeadLetterExchange,
                                      ShippingMessagingProperties properties) {
        return BindingBuilder.bind(trackingDeadLetterQueue).to(shippingDeadLetterExchange)
                .with(properties.trackingQueue());
    }
}
