package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unidades federativas do Brasil (26 estados + Distrito Federal), cada uma com sua região.
 *
 * <h2>Por que um enum em vez de guardar a UF como String?</h2>
 * Com String, {@code "SP"}, {@code "sp"} e {@code "Sao Paulo"} seriam valores diferentes, e um
 * erro de digitação viraria dado ruim no banco. O enum só admite as 27 UFs válidas e carrega o
 * conhecimento associado (a região), usado pelo cálculo de frete. A ViaCEP devolve a sigla
 * ({@code "uf": "SP"}); o adaptador a converte com {@link #fromAcronym(String)}.
 */
public enum BrazilianState {
    AC("Acre", Region.NORTH),
    AL("Alagoas", Region.NORTHEAST),
    AP("Amapá", Region.NORTH),
    AM("Amazonas", Region.NORTH),
    BA("Bahia", Region.NORTHEAST),
    CE("Ceará", Region.NORTHEAST),
    DF("Distrito Federal", Region.CENTER_WEST),
    ES("Espírito Santo", Region.SOUTHEAST),
    GO("Goiás", Region.CENTER_WEST),
    MA("Maranhão", Region.NORTHEAST),
    MT("Mato Grosso", Region.CENTER_WEST),
    MS("Mato Grosso do Sul", Region.CENTER_WEST),
    MG("Minas Gerais", Region.SOUTHEAST),
    PA("Pará", Region.NORTH),
    PB("Paraíba", Region.NORTHEAST),
    PR("Paraná", Region.SOUTH),
    PE("Pernambuco", Region.NORTHEAST),
    PI("Piauí", Region.NORTHEAST),
    RJ("Rio de Janeiro", Region.SOUTHEAST),
    RN("Rio Grande do Norte", Region.NORTHEAST),
    RS("Rio Grande do Sul", Region.SOUTH),
    RO("Rondônia", Region.NORTH),
    RR("Roraima", Region.NORTH),
    SC("Santa Catarina", Region.SOUTH),
    SP("São Paulo", Region.SOUTHEAST),
    SE("Sergipe", Region.NORTHEAST),
    TO("Tocantins", Region.NORTH);

    /**
     * Índice pré-calculado. Uma busca em {@code Map} é O(1), mas o motivo principal é outro:
     * {@code valueOf} lança {@link IllegalArgumentException} genérica para siglas desconhecidas,
     * e queremos uma exceção de domínio com mensagem clara.
     */
    private static final Map<String, BrazilianState> BY_ACRONYM = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    private final String fullName;
    private final Region region;

    BrazilianState(String fullName, Region region) {
        this.fullName = fullName;
        this.region = region;
    }

    /**
     * Converte uma sigla ({@code "sp"}, {@code " SP "}) na UF correspondente.
     *
     * @throws DomainValidationException se a sigla não corresponder a uma UF
     */
    public static BrazilianState fromAcronym(String acronym) {
        Objects.requireNonNull(acronym, "acronym must not be null");
        BrazilianState state = BY_ACRONYM.get(acronym.strip().toUpperCase(Locale.ROOT));
        if (state == null) {
            throw new DomainValidationException("INVALID_STATE", "UF inválida: " + acronym);
        }
        return state;
    }

    public String fullName() {
        return fullName;
    }

    public Region region() {
        return region;
    }
}
