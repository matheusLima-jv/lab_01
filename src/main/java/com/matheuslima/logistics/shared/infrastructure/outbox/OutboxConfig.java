package com.matheuslima.logistics.shared.infrastructure.outbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuração do outbox: liga o agendamento ({@code @Scheduled}) e registra as propriedades.
 *
 * <p>{@code @EnableConfigurationProperties(OutboxProperties.class)} registra o record de
 * propriedades como bean. É a forma explícita; a alternativa {@code @ConfigurationPropertiesScan}
 * na classe principal registraria todas de uma vez, mas também nos testes de fatia, que então
 * exigiriam valores para propriedades que nem usam.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {
}
