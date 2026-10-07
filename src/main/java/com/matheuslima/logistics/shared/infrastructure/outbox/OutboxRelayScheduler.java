package com.matheuslima.logistics.shared.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o {@link OutboxRelay} periodicamente.
 *
 * <h2>Por que separar o "quando" (esta classe) do "o quê" (o relay)?</h2>
 * <ul>
 *   <li>Testes de integração desligam o agendamento ({@code app.outbox.relay.enabled=false}) e
 *       chamam {@code relay.relayPendingMessages()} diretamente, sem depender de timing.</li>
 *   <li>O gatilho pode mudar (cron, fila, endpoint administrativo) sem tocar na lógica.</li>
 * </ul>
 *
 * <h2>{@code @ConditionalOnProperty}: bean que só existe se a configuração mandar</h2>
 * Se a propriedade for {@code false}, o Spring simplesmente não cria este bean, e sem bean não
 * há agendamento.
 *
 * <h2>{@code fixedDelay} e não {@code fixedRate}</h2>
 * {@code fixedDelay} espera o intervalo <i>depois</i> que a rodada termina, então rodadas nunca se
 * sobrepõem, mesmo quando uma demora (ex.: broker lento). {@code fixedRate} dispararia em
 * intervalos fixos e poderia empilhar execuções.
 *
 * <h2>E a latência?</h2>
 * O polling a cada 1 s adiciona até 1 s entre o commit e a publicação. Para a maioria dos casos
 * (rastreamento, notificações) é irrelevante. Quando não for, a evolução é o CDC (Change Data
 * Capture, ex.: Debezium lendo o WAL do PostgreSQL), que publica em milissegundos sem polling.
 */
@Component
@ConditionalOnProperty(prefix = "app.outbox.relay", name = "enabled", havingValue = "true")
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(initialDelayString = "${app.outbox.relay.initial-delay}",
            fixedDelayString = "${app.outbox.relay.fixed-delay}")
    public void relay() {
        try {
            relay.relayPendingMessages();
        } catch (RuntimeException unexpected) {
            // Um erro aqui (ex.: banco fora do ar) não pode matar o agendamento: logamos e a
            // próxima rodada tenta de novo.
            log.error("Outbox relay round failed", unexpected);
        }
    }
}
