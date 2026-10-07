package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;

import java.util.Objects;

/**
 * Endereço postal completo de origem ou de destino de um envio (Value Object).
 *
 * <h2>De onde vem cada campo?</h2>
 * Parte vem de quem chama a API e parte vem da ViaCEP, e essa divisão é intencional:
 * <ul>
 *   <li><b>Cliente:</b> CEP, número e complemento, que só a pessoa sabe.</li>
 *   <li><b>ViaCEP:</b> logradouro, bairro, cidade e UF. Não confiamos nesses campos vindos do
 *       cliente: a fonte oficial garante dados consistentes, e é a UF oficial que define o
 *       preço do frete. Se aceitássemos a UF do cliente, alguém poderia declarar "SP" para um
 *       endereço do AM e pagar frete local.</li>
 * </ul>
 *
 * <h2>Por que logradouro e bairro podem ser vazios?</h2>
 * Municípios pequenos têm CEP único (CEP "geral"), e para eles a ViaCEP devolve logradouro e
 * bairro vazios. Exigir esses campos tornaria impossível enviar para essas cidades, um bug de
 * regra de negócio disfarçado de validação.
 *
 * @param postalCode CEP do endereço
 * @param street     logradouro (pode ser vazio, veja acima)
 * @param number     número informado pelo cliente (texto: existem "S/N", "123-A", "KM 12")
 * @param complement complemento opcional ({@code null} quando ausente)
 * @param district   bairro (pode ser vazio)
 * @param city       cidade (obrigatória)
 * @param state      UF (obrigatória)
 */
public record Address(
        PostalCode postalCode,
        String street,
        String number,
        String complement,
        String district,
        String city,
        BrazilianState state) {

    public static final int MAX_NUMBER_LENGTH = 20;
    public static final int MAX_COMPLEMENT_LENGTH = 100;
    /** Limite das colunas de texto no banco; validar aqui evita um erro 500 vindo do SQL. */
    public static final int MAX_TEXT_LENGTH = 255;

    public Address {
        Objects.requireNonNull(postalCode, "postalCode must not be null");
        Objects.requireNonNull(number, "number must not be null");
        Objects.requireNonNull(city, "city must not be null");
        Objects.requireNonNull(state, "state must not be null");

        street = limit(normalize(street), MAX_TEXT_LENGTH, "street");
        district = limit(normalize(district), MAX_TEXT_LENGTH, "district");
        city = limit(city.strip(), MAX_TEXT_LENGTH, "city");
        number = limit(number.strip(), MAX_NUMBER_LENGTH, "number");
        complement = complement == null || complement.isBlank()
                ? null
                : limit(complement.strip(), MAX_COMPLEMENT_LENGTH, "complement");

        if (number.isEmpty()) {
            throw new DomainValidationException("INVALID_ADDRESS_NUMBER",
                    "O número do endereço é obrigatório (use \"S/N\" quando não houver).");
        }
        if (city.isEmpty()) {
            throw new DomainValidationException("INVALID_ADDRESS_CITY", "A cidade do endereço é obrigatória.");
        }
    }

    /**
     * Mesma cidade = mesma UF e mesmo nome de cidade (ignorando maiúsculas).
     *
     * <p><b>Limitação conhecida:</b> comparar por nome é frágil se as fontes escreverem o nome de
     * formas diferentes. Como os dois endereços vêm da mesma fonte (ViaCEP), a grafia é
     * consistente. A solução robusta seria comparar o código IBGE do município, que a ViaCEP
     * também devolve: fica como evolução documentada.
     */
    public boolean isSameCityAs(Address other) {
        return state == other.state && city.equalsIgnoreCase(other.city);
    }

    public boolean isSameStateAs(Address other) {
        return state == other.state;
    }

    public boolean isSameRegionAs(Address other) {
        return state.region() == other.state.region();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static String limit(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new DomainValidationException("INVALID_ADDRESS_FIELD",
                    "O campo '%s' do endereço excede %d caracteres.".formatted(field, maxLength));
        }
        return value;
    }
}
