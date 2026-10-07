package com.matheuslima.logistics.shipping.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Valor monetário: quantia + moeda (Value Object, um clássico do DDD).
 *
 * <h2>Por que {@link BigDecimal} e nunca {@code double}?</h2>
 * {@code double} é binário e não representa exatamente valores como 0,10. Em {@code double},
 * {@code 0.1 + 0.2 == 0.30000000000000004}. Em dinheiro, esse erro acumula e aparece em
 * relatórios e conciliações. {@code BigDecimal} representa casas decimais com exatidão.
 *
 * <h2>Por que a moeda faz parte do valor?</h2>
 * "10" não significa nada; "10 BRL" significa. Somar BRL com USD sem conversão é bug, e aqui
 * isso lança exceção em vez de produzir um número sem sentido.
 *
 * <h2>Por que normalizar a escala no construtor?</h2>
 * {@code BigDecimal.equals} considera a escala: {@code 10.0} e {@code 10.00} são "diferentes".
 * Fixando a escala da moeda (2 casas para BRL), dois {@code Money} com o mesmo valor ficam
 * sempre iguais, e o {@code equals} do record funciona como esperado.
 *
 * <h2>Por que {@link RoundingMode#HALF_EVEN}?</h2>
 * É o "arredondamento bancário": nos empates (x,xx5) arredonda para o par mais próximo. Ao longo
 * de muitas operações os arredondamentos para cima e para baixo se compensam, enquanto
 * {@code HALF_UP} enviesa o total para cima. As operações deste projeto (soma e multiplicação
 * por inteiro) nunca criam casas extras, então o arredondamento só atua sobre valores de
 * entrada com mais de duas casas.
 *
 * <p>Este tipo mora no contexto de envios porque só ele usa dinheiro hoje. Se outro contexto
 * precisar, ele sobe para o shared kernel. Generalizar antes da necessidade é especulação.
 *
 * @param amount   quantia, com a escala da moeda
 * @param currency moeda ISO 4217
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final Currency BRL = Currency.getInstance("BRL");

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /** Atalho para reais a partir de texto ({@code "12.50"}). Construir BigDecimal a partir de
     * String é exato; a partir de double herdaria o erro binário ({@code new BigDecimal(0.1)}). */
    public static Money brl(String amount) {
        return new Money(new BigDecimal(amount), BRL);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            // IllegalArgumentException e não exceção de domínio: misturar moedas é erro de
            // programação (bug), não entrada inválida de um cliente.
            throw new IllegalArgumentException(
                    "Currency mismatch: %s vs %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }
}
