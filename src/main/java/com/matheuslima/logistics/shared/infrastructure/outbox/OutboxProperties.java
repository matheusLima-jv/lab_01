package com.matheuslima.logistics.shared.infrastructure.outbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuração do outbox ({@code app.outbox.*} no application.yml).
 *
 * <h2>{@code @ConfigurationProperties} com record: configuração tipada</h2>
 * Em vez de espalhar {@code @Value("${app.outbox.batch-size}")} pelo código, as propriedades são
 * agrupadas em um objeto imutável, com tipos reais ({@link Duration} aceita "5s", "200ms") e
 * validação na subida ({@code @Validated}): um {@code batch-size: 0} impede a aplicação de subir,
 * com uma mensagem clara, em vez de causar um comportamento estranho em produção.
 * O {@code spring-boot-configuration-processor} gera metadados a partir desta classe, e a IDE
 * passa a autocompletar e documentar as chaves no YAML.
 *
 * <h2>Convenção do projeto: valores no YAML, validação no código</h2>
 * Nenhum record de propriedades tem valor padrão ({@code @DefaultValue}). O
 * {@code application.yml} é a fonte única dos valores; o código só valida. Valor padrão
 * duplicado em dois lugares é convite para divergência ("mudei no YAML, mas valia o do código").
 *
 * @param batchSize      mensagens por rodada do relay
 * @param confirmTimeout tempo máximo de espera pela confirmação do broker, por mensagem
 * @param relay          configuração do agendamento
 */
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @Min(1) @Max(500) int batchSize,
        @NotNull Duration confirmTimeout,
        @NotNull @Valid Relay relay) {

    /**
     * Lidas pelas anotações do {@link OutboxRelayScheduler} via placeholders. Declarar aqui as
     * valida e as documenta para a IDE.
     *
     * @param enabled      liga/desliga o agendamento (testes desligam para chamar o relay à mão)
     * @param initialDelay espera antes da primeira rodada, para a aplicação terminar de subir
     * @param fixedDelay   intervalo entre o fim de uma rodada e o início da próxima
     */
    public record Relay(boolean enabled, @NotNull Duration initialDelay, @NotNull Duration fixedDelay) {
    }
}
