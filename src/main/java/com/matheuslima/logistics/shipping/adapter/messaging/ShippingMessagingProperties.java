package com.matheuslima.logistics.shipping.adapter.messaging;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Nomes dos recursos do RabbitMQ usados pelo contexto de envios ({@code app.messaging.shipping.*}).
 *
 * <p>Nomes vêm de configuração e não de constantes porque é comum ambientes compartilharem o
 * mesmo broker com prefixos diferentes (ex.: {@code hml.logistics...}), e também porque o
 * time de plataforma pode impor convenções de nomenclatura.
 *
 * <p>Fica no pacote {@code adapter.messaging} (e não em {@code config}) porque é usado pelos
 * adaptadores de entrada e de saída. Assim a dependência entre pacotes tem uma direção só:
 * {@code config -> adapter -> application -> domain}, sem ciclos.
 *
 * <p>Convenção adotada: {@code <sistema>.<contexto>.<recurso>}, por exemplo
 * {@code logistics.shipping.events}. Nomes hierárquicos facilitam permissões por prefixo e a
 * leitura no painel de administração.
 *
 * @param exchange                exchange (topic) onde os eventos de envio são publicados
 * @param trackingQueue           fila consumida pela projeção de rastreamento
 * @param deadLetterExchange      exchange que recebe mensagens rejeitadas definitivamente
 * @param trackingDeadLetterQueue fila de "mensagens mortas" da projeção, para análise manual
 */
@Validated
@ConfigurationProperties(prefix = "app.messaging.shipping")
public record ShippingMessagingProperties(
        @NotBlank String exchange,
        @NotBlank String trackingQueue,
        @NotBlank String deadLetterExchange,
        @NotBlank String trackingDeadLetterQueue) {
}
