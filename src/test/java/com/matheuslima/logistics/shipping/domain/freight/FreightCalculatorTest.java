package com.matheuslima.logistics.shipping.domain.freight;

import com.matheuslima.logistics.shipping.ShippingFixtures;
import com.matheuslima.logistics.shipping.domain.Address;
import com.matheuslima.logistics.shipping.domain.Money;
import com.matheuslima.logistics.shipping.domain.Weight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do Domain Service de frete.
 *
 * <p>Cada linha da tabela abaixo é um exemplo de negócio com o valor esperado calculado à mão.
 * Exemplos concretos ("SP para Manaus com 30 kg custa R$ 110,00") são a melhor forma de validar
 * uma regra de preço com quem é dono dela; muitas vezes são eles que fornecem a tabela.
 */
@DisplayName("FreightCalculator (domain service)")
class FreightCalculatorTest {

    private final FreightCalculator calculator = new FreightCalculator(ShippingFixtures.defaultFreightTable());

    static Stream<Arguments> examples() {
        return Stream.of(
                // origem, destino, gramas, rota esperada, frete esperado
                Arguments.of(ShippingFixtures.saoPauloSe(), ShippingFixtures.saoPauloPaulista(), 500,
                        RouteType.SAME_CITY, "14.50"),   // 12,00 + 2,50 x 1
                Arguments.of(ShippingFixtures.saoPauloSe(), ShippingFixtures.campinas(), 1_500,
                        RouteType.SAME_STATE, "23.00"),  // 18,00 + 2,50 x 2
                Arguments.of(ShippingFixtures.saoPauloSe(), ShippingFixtures.rioDeJaneiro(), 1_500,
                        RouteType.SAME_REGION, "30.00"), // 25,00 + 2,50 x 2
                Arguments.of(ShippingFixtures.saoPauloSe(), ShippingFixtures.manaus(), 30_000,
                        RouteType.INTERREGIONAL, "110.00") // 35,00 + 2,50 x 30
        );
    }

    @ParameterizedTest(name = "{0} -> {1}, {2} g: {3} = R$ {4}")
    @MethodSource("examples")
    void calculatesFreight(Address origin, Address destination, int grams, RouteType route, String expected) {
        assertThat(RouteType.between(origin, destination)).isEqualTo(route);
        assertThat(calculator.calculate(origin, destination, Weight.ofGrams(grams))).isEqualTo(Money.brl(expected));
    }

    @Test
    @DisplayName("é simétrico: ida e volta custam o mesmo")
    void isSymmetric() {
        Weight weight = Weight.ofGrams(2_000);

        assertThat(calculator.calculate(ShippingFixtures.manaus(), ShippingFixtures.saoPauloSe(), weight))
                .isEqualTo(calculator.calculate(ShippingFixtures.saoPauloSe(), ShippingFixtures.manaus(), weight));
    }

    @Test
    @DisplayName("tabela rejeita valores negativos e moedas misturadas (erro de configuração)")
    void tableValidation() {
        Money usd = Money.of(BigDecimal.ONE, Currency.getInstance("USD"));

        assertThatThrownBy(() -> new FreightTable(Money.brl("-1"), Money.brl("1"), Money.brl("1"), Money.brl("1"),
                Money.brl("1"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negative");
        assertThatThrownBy(() -> new FreightTable(Money.brl("1"), Money.brl("1"), Money.brl("1"), Money.brl("1"),
                usd)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currency");
    }
}
