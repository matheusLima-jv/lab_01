package com.matheuslima.logistics.identity.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Endereço de e-mail validado e normalizado: um exemplo clássico de <b>Value Object</b>.
 *
 * <h2>O que caracteriza um Value Object (DDD)?</h2>
 * <ul>
 *   <li><b>Sem identidade:</b> dois e-mails iguais são intercambiáveis; o que importa é o valor.</li>
 *   <li><b>Imutável:</b> para "mudar" o e-mail, cria-se outro. Um {@code record} dá isso de graça.</li>
 *   <li><b>Sempre válido:</b> a validação está no construtor, então se existe um {@code Email},
 *       ele é válido. Nenhum código precisa revalidar ("parse, don't validate").</li>
 * </ul>
 *
 * <h2>Por que normalizar para minúsculas?</h2>
 * Pela RFC 5321 a parte local ({@code Fulano} em {@code Fulano@x.com}) poderia diferenciar
 * maiúsculas, mas na prática nenhum provedor relevante faz isso. Sem normalizar, a mesma pessoa
 * conseguiria criar duas contas ({@code Ana@x.com} e {@code ana@x.com}), e a constraint UNIQUE
 * do banco não pegaria.
 *
 * <h2>Por que {@code Locale.ROOT} no {@code toLowerCase}?</h2>
 * Sem locale explícito, a JVM usa o idioma do servidor. Em turco, {@code "I".toLowerCase()} vira
 * {@code "ı"} (i sem ponto), e um e-mail válido deixaria de bater com o que está no banco.
 * {@code Locale.ROOT} torna a conversão determinística em qualquer servidor.
 *
 * <h2>Por que uma regex "simples"?</h2>
 * A gramática completa da RFC 5322 aceita coisas como {@code "a b"@[127.0.0.1]}, que nenhum
 * cadastro real quer aceitar. A regex abaixo cobre os e-mails usados no mundo real. A única
 * validação definitiva de um e-mail é mandar uma mensagem de confirmação (fora do escopo).
 *
 * @param value e-mail normalizado (minúsculo, sem espaços nas pontas)
 */
public record Email(String value) {

    /** RFC 5321: o caminho completo de um endereço não passa de 254 caracteres. */
    public static final int MAX_LENGTH = 254;

    private static final Pattern FORMAT =
            Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");

    public Email {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new DomainValidationException("INVALID_EMAIL", "E-mail inválido.");
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
