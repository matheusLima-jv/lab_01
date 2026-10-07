package com.matheuslima.logistics.shipping.config;

import com.matheuslima.logistics.shipping.domain.Money;
import com.matheuslima.logistics.shipping.domain.freight.FreightTable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Regras de negócio configuráveis do contexto de envios ({@code app.shipping.*}).
 *
 * <h2>Configuração de negócio entrando no domínio, sem acoplar o domínio ao Spring</h2>
 * O domínio define a estrutura e as regras de consistência da tabela ({@link FreightTable}); este
 * record só lê os valores do YAML (o Spring converte {@code "BRL"} em {@link Currency} e
 * {@code 12.00} em {@link BigDecimal}) e os entrega convertidos ({@link Freight#toFreightTable()}).
 * O domínio continua sem saber o que é {@code @ConfigurationProperties}.
 *
 * @param freight tabela de frete
 */
@Validated
@ConfigurationProperties(prefix = "app.shipping")
public record ShippingProperties(@NotNull @Valid Freight freight) {

    public record Freight(
            @NotNull Currency currency,
            @NotNull @PositiveOrZero BigDecimal sameCityBase,
            @NotNull @PositiveOrZero BigDecimal sameStateBase,
            @NotNull @PositiveOrZero BigDecimal sameRegionBase,
            @NotNull @PositiveOrZero BigDecimal interregionalBase,
            @NotNull @PositiveOrZero BigDecimal perKilogram) {

        public FreightTable toFreightTable() {
            return new FreightTable(
                    Money.of(sameCityBase, currency),
                    Money.of(sameStateBase, currency),
                    Money.of(sameRegionBase, currency),
                    Money.of(interregionalBase, currency),
                    Money.of(perKilogram, currency));
        }
    }
}
