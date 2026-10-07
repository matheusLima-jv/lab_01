package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;
import com.matheuslima.logistics.shipping.ShippingFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes dos Value Objects do contexto de envios.
 *
 * <p>Value Objects concentram validação e normalização, então os testes cobrem três coisas:
 * (1) valores válidos são aceitos e normalizados, (2) valores inválidos são rejeitados com
 * {@link DomainValidationException} e um código estável, (3) {@code null} é tratado como bug
 * ({@link NullPointerException}), conforme a política explicada em
 * {@link DomainValidationException}.
 */
@DisplayName("Value Objects de envio")
class ValueObjectsTest {

    @Nested
    @DisplayName("PostalCode (CEP)")
    class PostalCodeTests {

        @ParameterizedTest(name = "aceita \"{0}\"")
        @ValueSource(strings = {"01001-000", "01001000", " 01001-000 "})
        void acceptsValidFormats(String input) {
            PostalCode cep = PostalCode.of(input);

            assertThat(cep.value()).isEqualTo("01001000");
            assertThat(cep.formatted()).isEqualTo("01001-000");
            assertThat(cep).hasToString("01001-000");
        }

        @ParameterizedTest(name = "rejeita \"{0}\"")
        @ValueSource(strings = {"", "0100100", "010010000", "01001-0000", "abcde-fgh", "01.001-000", "../admin"})
        void rejectsInvalidFormats(String input) {
            assertThatThrownBy(() -> PostalCode.of(input))
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            ex -> assertThat(ex.code()).isEqualTo("INVALID_POSTAL_CODE"));
        }

        @Test
        @DisplayName("null é bug, não entrada inválida")
        void nullIsABug() {
            assertThatNullPointerException().isThrownBy(() -> PostalCode.of(null));
        }
    }

    @Nested
    @DisplayName("BrazilianState (UF)")
    class BrazilianStateTests {

        @ParameterizedTest(name = "\"{0}\" -> {1} ({2})")
        @CsvSource({"SP, SP, SOUTHEAST", "sp, SP, SOUTHEAST", "' am ', AM, NORTH", "DF, DF, CENTER_WEST",
                "RS, RS, SOUTH", "BA, BA, NORTHEAST"})
        void resolvesAcronymAndRegion(String input, BrazilianState expected, Region region) {
            BrazilianState state = BrazilianState.fromAcronym(input);

            assertThat(state).isEqualTo(expected);
            assertThat(state.region()).isEqualTo(region);
        }

        @Test
        @DisplayName("tem as 27 UFs e nomes completos em português")
        void hasAllStates() {
            assertThat(BrazilianState.values()).hasSize(27);
            assertThat(BrazilianState.SP.fullName()).isEqualTo("São Paulo");
            assertThat(Region.CENTER_WEST.displayName()).isEqualTo("Centro-Oeste");
        }

        @Test
        @DisplayName("rejeita sigla desconhecida")
        void rejectsUnknownAcronym() {
            assertThatThrownBy(() -> BrazilianState.fromAcronym("XX"))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("XX");
        }
    }

    @Nested
    @DisplayName("Address")
    class AddressTests {

        @Test
        @DisplayName("normaliza espaços e trata complemento em branco como ausente")
        void normalizesFields() {
            Address address = new Address(PostalCode.of("01001000"), " Praça da Sé ", " 100 ", "   ", null,
                    " São Paulo ", BrazilianState.SP);

            assertThat(address.street()).isEqualTo("Praça da Sé");
            assertThat(address.number()).isEqualTo("100");
            assertThat(address.complement()).isNull();
            assertThat(address.district()).isEmpty();
            assertThat(address.city()).isEqualTo("São Paulo");
        }

        @Test
        @DisplayName("aceita logradouro vazio (cidades com CEP único)")
        void acceptsEmptyStreetForSingleCepCities() {
            Address address = new Address(PostalCode.of("78175-000"), "", "S/N", null, "", "Poconé",
                    BrazilianState.MT);

            assertThat(address.street()).isEmpty();
        }

        @Test
        @DisplayName("exige número e cidade")
        void requiresNumberAndCity() {
            assertThatThrownBy(() -> new Address(PostalCode.of("01001000"), "Rua", " ", null, "Bairro", "São Paulo",
                    BrazilianState.SP))
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            ex -> assertThat(ex.code()).isEqualTo("INVALID_ADDRESS_NUMBER"));
            assertThatThrownBy(() -> new Address(PostalCode.of("01001000"), "Rua", "1", null, "Bairro", " ",
                    BrazilianState.SP))
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            ex -> assertThat(ex.code()).isEqualTo("INVALID_ADDRESS_CITY"));
        }

        @Test
        @DisplayName("limita o tamanho dos campos para não estourar as colunas do banco")
        void limitsFieldLengths() {
            String longNumber = "1".repeat(Address.MAX_NUMBER_LENGTH + 1);
            String longComplement = "c".repeat(Address.MAX_COMPLEMENT_LENGTH + 1);

            assertThatThrownBy(() -> new Address(PostalCode.of("01001000"), "Rua", longNumber, null, "B", "SP",
                    BrazilianState.SP)).isInstanceOf(DomainValidationException.class);
            assertThatThrownBy(() -> new Address(PostalCode.of("01001000"), "Rua", "1", longComplement, "B", "SP",
                    BrazilianState.SP)).isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("compara cidade, estado e região")
        void comparesLocality() {
            Address se = ShippingFixtures.saoPauloSe();

            assertThat(se.isSameCityAs(ShippingFixtures.saoPauloPaulista())).isTrue();
            assertThat(se.isSameCityAs(ShippingFixtures.campinas())).isFalse();
            assertThat(se.isSameStateAs(ShippingFixtures.campinas())).isTrue();
            assertThat(se.isSameRegionAs(ShippingFixtures.rioDeJaneiro())).isTrue();
            assertThat(se.isSameRegionAs(ShippingFixtures.manaus())).isFalse();
        }
    }

    @Nested
    @DisplayName("Weight")
    class WeightTests {

        @ParameterizedTest(name = "{0} g = {1} kg iniciados")
        @CsvSource({"1, 1", "999, 1", "1000, 1", "1001, 2", "1500, 2", "29999, 30", "30000, 30"})
        void roundsKilogramsUp(int grams, int expectedKilograms) {
            assertThat(Weight.ofGrams(grams).kilogramsRoundedUp()).isEqualTo(expectedKilograms);
        }

        @ParameterizedTest(name = "rejeita {0} g")
        @ValueSource(ints = {0, -1, 30_001, Integer.MAX_VALUE})
        void rejectsOutOfRange(int grams) {
            assertThatThrownBy(() -> Weight.ofGrams(grams))
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            ex -> assertThat(ex.code()).isEqualTo("INVALID_WEIGHT"));
        }
    }

    @Nested
    @DisplayName("Money")
    class MoneyTests {

        @Test
        @DisplayName("normaliza a escala: 10, 10.0 e 10.00 são iguais")
        void normalizesScale() {
            assertThat(Money.brl("10")).isEqualTo(Money.brl("10.00"));
            assertThat(Money.of(new BigDecimal("10.0"), Money.BRL)).isEqualTo(Money.brl("10.00"));
            assertThat(Money.brl("10").amount()).hasToString("10.00");
        }

        @Test
        @DisplayName("usa arredondamento bancário (HALF_EVEN) nos empates")
        void usesBankersRounding() {
            assertThat(Money.brl("10.005").amount()).isEqualByComparingTo("10.00");
            assertThat(Money.brl("10.015").amount()).isEqualByComparingTo("10.02");
        }

        @Test
        @DisplayName("soma e multiplica sem erro de ponto flutuante")
        void arithmetic() {
            assertThat(Money.brl("0.10").plus(Money.brl("0.20"))).isEqualTo(Money.brl("0.30"));
            assertThat(Money.brl("2.50").times(3)).isEqualTo(Money.brl("7.50"));
            assertThat(Money.brl("-0.01").isNegative()).isTrue();
            assertThat(Money.brl("0.00").isNegative()).isFalse();
            assertThat(Money.brl("12.5")).hasToString("BRL 12.50");
        }

        @Test
        @DisplayName("recusa somar moedas diferentes")
        void rejectsCurrencyMismatch() {
            Money usd = Money.of(BigDecimal.ONE, Currency.getInstance("USD"));

            assertThatThrownBy(() -> Money.brl("1.00").plus(usd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BRL")
                    .hasMessageContaining("USD");
        }
    }

    @Nested
    @DisplayName("identificadores")
    class Identifiers {

        @Test
        @DisplayName("ShipmentId e CustomerId exibem o UUID e não aceitam null")
        void identifiers() {
            ShipmentId id = ShipmentId.newId();

            assertThat(ShipmentId.of(id.value())).isEqualTo(id);
            assertThat(id).hasToString(id.value().toString());
            assertThat(CustomerId.of(id.value())).hasToString(id.value().toString());
            assertThatNullPointerException().isThrownBy(() -> ShipmentId.of(null));
            assertThatNullPointerException().isThrownBy(() -> CustomerId.of(null));
        }
    }
}
