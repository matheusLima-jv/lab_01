package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;

/**
 * Peso de um pacote, em gramas.
 *
 * <h2>Por que gramas inteiros e não quilos com casas decimais?</h2>
 * Quando existe uma unidade menor natural, inteiros evitam toda a classe de problemas de
 * arredondamento ({@code 0.1 + 0.2 != 0.3} em {@code double}). É o mesmo princípio de guardar
 * dinheiro em centavos. Precisão de 1 grama é mais do que suficiente para logística.
 *
 * <h2>Por que o limite de 30 kg?</h2>
 * É uma regra de negócio realista: serviços de encomenda como PAC e SEDEX limitam pacotes a
 * 30 kg. Acima disso o envio é carga, com outro processo, outro preço e outro tipo de veículo.
 * Modelar o limite no domínio impede que o sistema aceite algo que a operação não consegue
 * cumprir.
 *
 * @param grams peso em gramas, entre {@value #MIN_GRAMS} e {@value #MAX_GRAMS}
 */
public record Weight(int grams) {

    public static final int MIN_GRAMS = 1;
    public static final int MAX_GRAMS = 30_000;

    public Weight {
        if (grams < MIN_GRAMS || grams > MAX_GRAMS) {
            throw new DomainValidationException("INVALID_WEIGHT",
                    "O peso deve estar entre %d g e %d g (30 kg).".formatted(MIN_GRAMS, MAX_GRAMS));
        }
    }

    public static Weight ofGrams(int grams) {
        return new Weight(grams);
    }

    /**
     * Quilos "iniciados", como cobram as transportadoras: 1 g a 1.000 g contam como 1 kg,
     * 1.001 g contam como 2 kg. A conta é feita só com inteiros: {@code (g + 999) / 1000}
     * equivale a arredondar a divisão para cima, sem converter para ponto flutuante.
     */
    public int kilogramsRoundedUp() {
        return (grams + 999) / 1000;
    }
}
