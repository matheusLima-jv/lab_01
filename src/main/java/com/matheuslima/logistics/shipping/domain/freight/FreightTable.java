package com.matheuslima.logistics.shipping.domain.freight;

import com.matheuslima.logistics.shipping.domain.Money;

import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Tabela de preços do frete: taxa base por tipo de rota + valor por quilo iniciado.
 *
 * <h2>Por que a tabela é um Value Object do domínio e não constantes no código?</h2>
 * Preço muda (reajuste, promoção, tabela por cliente). Se os valores fossem constantes, cada
 * reajuste exigiria um deploy. Aqui o domínio define a <i>estrutura</i> da tabela e suas regras
 * de consistência; os <i>valores</i> vêm de configuração ({@code app.shipping.freight.*} no
 * {@code application.yml}), convertidos para este objeto em {@code ShippingConfig}.
 * O domínio continua sem saber que o Spring existe.
 *
 * <p>Evolução natural: carregar a tabela do banco, permitindo reajuste sem reiniciar a
 * aplicação. A interface do {@link FreightCalculator} não mudaria.
 *
 * @param sameCityBase      taxa base para entregas na mesma cidade
 * @param sameStateBase     taxa base para entregas no mesmo estado
 * @param sameRegionBase    taxa base para entregas na mesma região
 * @param interregionalBase taxa base para entregas entre regiões
 * @param perKilogram       valor cobrado por quilo iniciado
 */
public record FreightTable(
        Money sameCityBase,
        Money sameStateBase,
        Money sameRegionBase,
        Money interregionalBase,
        Money perKilogram) {

    public FreightTable {
        List<Money> all = List.of(
                Objects.requireNonNull(sameCityBase, "sameCityBase must not be null"),
                Objects.requireNonNull(sameStateBase, "sameStateBase must not be null"),
                Objects.requireNonNull(sameRegionBase, "sameRegionBase must not be null"),
                Objects.requireNonNull(interregionalBase, "interregionalBase must not be null"),
                Objects.requireNonNull(perKilogram, "perKilogram must not be null"));

        // Erros aqui são de CONFIGURAÇÃO, detectados na subida da aplicação (fail fast):
        // melhor não subir do que subir cobrando frete negativo ou em moedas misturadas.
        if (all.stream().anyMatch(Money::isNegative)) {
            throw new IllegalArgumentException("freight table values must not be negative");
        }
        Currency currency = sameCityBase.currency();
        if (all.stream().anyMatch(money -> !money.currency().equals(currency))) {
            throw new IllegalArgumentException("freight table values must share the same currency");
        }
    }

    /** Taxa base da rota. O {@code switch} exaustivo garante que toda rota tem preço. */
    public Money baseFee(RouteType route) {
        return switch (route) {
            case SAME_CITY -> sameCityBase;
            case SAME_STATE -> sameStateBase;
            case SAME_REGION -> sameRegionBase;
            case INTERREGIONAL -> interregionalBase;
        };
    }
}
