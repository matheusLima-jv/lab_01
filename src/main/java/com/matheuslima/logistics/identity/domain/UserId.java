package com.matheuslima.logistics.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade de um usuário.
 *
 * <h2>Por que um tipo próprio em vez de {@code UUID} solto?</h2>
 * Com {@code UUID} em todo lugar, nada impede trocar a ordem de dois parâmetros
 * ({@code transfer(UUID userId, UUID shipmentId)} chamado com os argumentos invertidos), e o
 * compilador não reclama. Com tipos dedicados ("Value Object de identidade"), esse erro vira
 * erro de compilação. Além disso, o código passa a dizer o que cada valor significa.
 *
 * <h2>Por que o domínio gera o ID, e não o banco ({@code SERIAL}/{@code IDENTITY})?</h2>
 * Com o ID gerado na aplicação, o agregado já nasce identificado: podemos registrar eventos
 * com o ID, devolvê-lo na resposta e montar o header {@code Location}, tudo antes do
 * INSERT. Também evita expor IDs sequenciais, que permitem a um atacante enumerar recursos
 * ({@code /users/1}, {@code /users/2}...).
 *
 * @param value valor do UUID
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
