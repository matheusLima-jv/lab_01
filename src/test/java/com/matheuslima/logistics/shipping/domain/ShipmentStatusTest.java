package com.matheuslima.logistics.shipping.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tabela-verdade completa da máquina de estados.
 *
 * <p>Com 4 estados há 16 pares possíveis. Testar todos via {@link ParameterizedTest} documenta a
 * regra inteira em formato de tabela, fácil de revisar com alguém do negócio: "esta linha diz que
 * dá para cancelar em trânsito; está certo?".
 */
@DisplayName("ShipmentStatus (máquina de estados)")
class ShipmentStatusTest {

    @ParameterizedTest(name = "{0} -> {1} permitido? {2}")
    @CsvSource({
            "CREATED,    CREATED,    false",
            "CREATED,    IN_TRANSIT, true",
            "CREATED,    DELIVERED,  false",
            "CREATED,    CANCELLED,  true",
            "IN_TRANSIT, CREATED,    false",
            "IN_TRANSIT, IN_TRANSIT, false",
            "IN_TRANSIT, DELIVERED,  true",
            "IN_TRANSIT, CANCELLED,  false",
            "DELIVERED,  CREATED,    false",
            "DELIVERED,  IN_TRANSIT, false",
            "DELIVERED,  DELIVERED,  false",
            "DELIVERED,  CANCELLED,  false",
            "CANCELLED,  CREATED,    false",
            "CANCELLED,  IN_TRANSIT, false",
            "CANCELLED,  DELIVERED,  false",
            "CANCELLED,  CANCELLED,  false"
    })
    void transitionTable(ShipmentStatus from, ShipmentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    @DisplayName("apenas DELIVERED e CANCELLED são terminais")
    void terminalStates() {
        assertThat(ShipmentStatus.CREATED.isTerminal()).isFalse();
        assertThat(ShipmentStatus.IN_TRANSIT.isTerminal()).isFalse();
        assertThat(ShipmentStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(ShipmentStatus.CANCELLED.isTerminal()).isTrue();
    }
}
