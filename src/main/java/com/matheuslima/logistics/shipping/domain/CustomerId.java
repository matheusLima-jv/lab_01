package com.matheuslima.logistics.shipping.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade do cliente dono de um envio.
 *
 * <h2>Por que não reutilizar o {@code UserId} do contexto de identidade?</h2>
 * Se o contexto de envios importasse tipos do contexto de identidade, os dois ficariam
 * acoplados: qualquer mudança em {@code identity.domain} poderia quebrar {@code shipping}. Em
 * DDD, contextos se referenciam apenas por <b>identificador</b> (o mesmo UUID), cada um com o
 * seu próprio tipo. Essa independência é verificada pelo ArchUnit e é o que permitiria, no
 * futuro, separar os contextos em serviços diferentes sem reescrever o domínio.
 *
 * @param value UUID do usuário dono do envio (claim {@code sub} do JWT)
 */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
