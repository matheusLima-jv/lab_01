package com.matheuslima.logistics.shipping.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade de um envio. Veja a justificativa de IDs tipados e gerados pela aplicação em
 * {@code identity.domain.UserId}; o raciocínio é o mesmo.
 *
 * <p><b>Evolução possível:</b> UUID v7 (ordenado por tempo) melhora a localidade do índice
 * B-tree no PostgreSQL em tabelas grandes, porque inserções novas vão para o "fim" do índice
 * em vez de posições aleatórias. Para o volume deste projeto, o UUID v4 aleatório é suficiente.
 *
 * @param value valor do UUID
 */
public record ShipmentId(UUID value) {

    public ShipmentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ShipmentId newId() {
        return new ShipmentId(UUID.randomUUID());
    }

    public static ShipmentId of(UUID value) {
        return new ShipmentId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
