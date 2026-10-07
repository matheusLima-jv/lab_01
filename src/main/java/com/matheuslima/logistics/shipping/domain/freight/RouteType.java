package com.matheuslima.logistics.shipping.domain.freight;

import com.matheuslima.logistics.shipping.domain.Address;

/**
 * Classificação da rota entre origem e destino, do trecho mais curto ao mais longo.
 *
 * <p>É uma aproximação de distância baseada apenas em geografia administrativa (cidade, UF,
 * região). Transportadoras reais usam faixas de CEP e matrizes de distância, mas a estrutura do
 * código seria a mesma: uma regra pura que classifica a rota e uma tabela que dá o preço.
 */
public enum RouteType {

    SAME_CITY,
    SAME_STATE,
    SAME_REGION,
    INTERREGIONAL;

    /**
     * Classifica a rota. A ordem das verificações importa: do mais específico para o mais
     * geral (mesma cidade implica mesmo estado, que implica mesma região).
     */
    public static RouteType between(Address origin, Address destination) {
        if (origin.isSameCityAs(destination)) {
            return SAME_CITY;
        }
        if (origin.isSameStateAs(destination)) {
            return SAME_STATE;
        }
        if (origin.isSameRegionAs(destination)) {
            return SAME_REGION;
        }
        return INTERREGIONAL;
    }
}
