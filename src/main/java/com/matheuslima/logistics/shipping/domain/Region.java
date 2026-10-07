package com.matheuslima.logistics.shipping.domain;

/**
 * Macrorregiões do Brasil segundo o IBGE. Usadas no cálculo de frete: entregas dentro da
 * mesma região são mais baratas do que entregas inter-regionais.
 *
 * <p>Conhecimento estável do domínio (a divisão regional muda raramente) codificado como
 * {@code enum}: é verificado pelo compilador e não precisa de consulta externa.
 */
public enum Region {
    NORTH("Norte"),
    NORTHEAST("Nordeste"),
    CENTER_WEST("Centro-Oeste"),
    SOUTHEAST("Sudeste"),
    SOUTH("Sul");

    private final String displayName;

    Region(String displayName) {
        this.displayName = displayName;
    }

    /** Nome em português, para exibição. */
    public String displayName() {
        return displayName;
    }
}
