package com.matheuslima.logistics.shared.infrastructure.messaging;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuração transversal de mensageria (vale para todos os contextos).
 *
 * <h2>Por que JSON e não a serialização Java padrão?</h2>
 * Sem conversor, o Spring AMQP usa a serialização nativa do Java ({@code Serializable}), que:
 * <ul>
 *   <li>só é legível por consumidores Java com as <i>mesmas classes</i> no classpath;</li>
 *   <li>quebra com qualquer mudança na classe;</li>
 *   <li>é um vetor conhecido de ataques (desserialização insegura, OWASP).</li>
 * </ul>
 * JSON é legível por qualquer linguagem, inspecionável no painel do RabbitMQ e tolera evolução
 * (campos novos são ignorados por consumidores antigos).
 *
 * <h2>Por que reutilizar o {@link JsonMapper} do Spring Boot?</h2>
 * O bean auto-configurado já tem os módulos e convenções da aplicação (datas ISO-8601, por
 * exemplo). Criar outro mapper com configuração diferente faria a mesma data sair em formatos
 * distintos na API e nas mensagens.
 *
 * <p>Como existe um bean {@link MessageConverter}, o Spring Boot o aplica automaticamente ao
 * {@code RabbitTemplate} e às fábricas de listeners ({@code @RabbitListener}).
 */
@Configuration(proxyBeanMethods = false)
public class MessagingConfig {

    @Bean
    public MessageConverter amqpMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
