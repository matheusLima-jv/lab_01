package com.matheuslima.logistics.shared.infrastructure.outbox;

import com.matheuslima.logistics.shared.application.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Move mensagens do outbox (PostgreSQL) para o broker (RabbitMQ): o "relay" do padrão
 * Transactional Outbox.
 *
 * <h2>Algoritmo de uma rodada</h2>
 * <ol>
 *   <li>Abre uma transação e trava um lote de mensagens pendentes
 *       ({@code FOR UPDATE SKIP LOCKED}: outras instâncias pegam outros lotes).</li>
 *   <li>Para cada mensagem: publica, espera a confirmação do broker e marca como publicada.</li>
 *   <li>No commit, as marcações são gravadas (dirty checking do JPA) e as travas liberadas.</li>
 * </ol>
 *
 * <h2>Garantia de entrega: "pelo menos uma vez"</h2>
 * Se a aplicação cair <i>depois</i> de o broker confirmar e <i>antes</i> do commit, a mensagem
 * continua pendente no banco e será publicada de novo na próxima rodada: uma duplicata. Isso é
 * inevitável sem transação distribuída, e é aceitável porque os consumidores são idempotentes
 * (deduplicam pelo {@code messageId}). Entre perder eventos e eventualmente duplicá-los,
 * duplicar é o problema que tem solução simples.
 *
 * <h2>Por que parar o lote na primeira falha?</h2>
 * Falha de publicação quase sempre significa broker fora do ar, e então todas as próximas
 * mensagens também falhariam, cada uma esperando o timeout de confirmação com a transação aberta.
 * Interromper libera as travas rápido; a próxima rodada tenta de novo. Quando o broker voltar,
 * a fila de pendências escoa sozinha, na ordem de criação.
 *
 * <h2>Ordem de entrega</h2>
 * Dentro de uma instância, as mensagens saem em ordem de criação. Com várias instâncias, dois
 * eventos do mesmo envio podem sair fora de ordem. Por isso a projeção de rastreamento ordena por
 * {@code occurredAt} e não pela ordem de chegada. Consumidores devem ser projetados assim.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMessageRepository repository;
    private final RabbitOutboxPublisher publisher;
    private final TransactionRunner transactions;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxMessageRepository repository, RabbitOutboxPublisher publisher,
                       TransactionRunner transactions, OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Executa uma rodada do relay.
     *
     * @return quantas mensagens foram publicadas nesta rodada
     */
    public int relayPendingMessages() {
        return transactions.inTransaction(() -> {
            List<OutboxMessage> batch = repository.lockNextPendingBatch(properties.batchSize());
            int published = 0;
            for (OutboxMessage message : batch) {
                try {
                    publisher.publish(message);
                    message.markPublished(clock.instant());
                    published++;
                } catch (OutboxPublishException failure) {
                    message.markFailed(failure.getMessage());
                    log.warn("Outbox relay stopped: message {} (routingKey={}, attempt {}) was not published: {}",
                            message.getId(), message.getRoutingKey(), message.getAttempts(), failure.getMessage());
                    break;
                }
            }
            if (published > 0) {
                log.debug("Outbox relay published {} message(s)", published);
            }
            return published;
        });
    }
}
