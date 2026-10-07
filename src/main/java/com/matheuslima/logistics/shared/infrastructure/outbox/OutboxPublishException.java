package com.matheuslima.logistics.shared.infrastructure.outbox;

/**
 * Falha ao entregar uma mensagem do outbox ao broker (sem confirmação, nack ou erro de conexão).
 *
 * <p>É exceção de infraestrutura: nunca chega ao núcleo nem a um cliente HTTP. Ela existe só
 * para o relay saber que deve registrar a falha e tentar de novo depois.
 */
public class OutboxPublishException extends RuntimeException {

    public OutboxPublishException(String message) {
        super(message);
    }

    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
