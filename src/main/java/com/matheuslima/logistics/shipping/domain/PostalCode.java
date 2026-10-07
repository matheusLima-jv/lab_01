package com.matheuslima.logistics.shipping.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * CEP (Código de Endereçamento Postal) brasileiro.
 *
 * <h2>Por que validar o formato aqui se a ViaCEP vai validar de qualquer jeito?</h2>
 * <ul>
 *   <li><b>Custo:</b> um CEP com 7 dígitos é rejeitado em microssegundos, sem gastar uma chamada
 *       de rede (que custa latência e, em APIs pagas, dinheiro).</li>
 *   <li><b>Segurança:</b> o valor vai compor uma URL ({@code /ws/{cep}/json/}). Aceitar apenas
 *       8 dígitos elimina qualquer tentativa de injetar caminhos ou parâmetros na chamada
 *       externa (ex.: {@code ../admin}).</li>
 *   <li><b>Separação de responsabilidades:</b> o <i>formato</i> é uma regra do domínio. Se o
 *       CEP <i>existe</i> é informação que só a fonte externa tem; essa checagem fica na porta
 *       {@code PostalCodeLookup}.</li>
 * </ul>
 *
 * <p>Aceitamos {@code 01001-000} e {@code 01001000} (formas comuns de digitação) e
 * normalizamos para só dígitos, o que facilita comparação, cache e persistência.
 *
 * @param value CEP normalizado com 8 dígitos, sem hífen
 */
public record PostalCode(String value) {

    // \d em Java casa apenas dígitos ASCII (0-9), a menos que UNICODE_CHARACTER_CLASS seja
    // ligado. Isso impede que dígitos de outros alfabetos (ex.: árabe-índicos) passem.
    private static final Pattern FORMAT = Pattern.compile("^\\d{5}-?\\d{3}$");

    public PostalCode {
        Objects.requireNonNull(value, "value must not be null");
        String candidate = value.strip();
        if (!FORMAT.matcher(candidate).matches()) {
            throw new DomainValidationException("INVALID_POSTAL_CODE",
                    "CEP inválido: use o formato 00000-000 ou 00000000.");
        }
        value = candidate.replace("-", "");
    }

    public static PostalCode of(String value) {
        return new PostalCode(value);
    }

    /** Formato de exibição, com hífen ({@code 01001-000}). */
    public String formatted() {
        return value.substring(0, 5) + "-" + value.substring(5);
    }

    @Override
    public String toString() {
        return formatted();
    }
}
