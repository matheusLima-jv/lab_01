package com.matheuslima.logistics.identity.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Senha em texto puro, como digitada pela pessoa. Existe só pelo tempo necessário para ser
 * transformada em hash (cadastro) ou comparada com um hash (login).
 *
 * <h2>Por que um tipo dedicado para algo que é só uma String?</h2>
 * <ul>
 *   <li><b>Evita vazamento em logs:</b> {@link #toString()} devolve uma máscara. Um
 *       {@code log.info("cmd={}", command)} distraído não expõe a senha.</li>
 *   <li><b>Concentra a política de senha</b> em um único lugar do domínio.</li>
 *   <li><b>Deixa a intenção explícita</b> na assinatura: {@code hash(RawPassword)} não aceita
 *       uma String qualquer por engano.</li>
 * </ul>
 *
 * <h2>Política de senha: por que só tamanho?</h2>
 * Seguimos a orientação do NIST SP 800-63B:
 * <ul>
 *   <li><b>Mínimo de 8 caracteres:</b> o tamanho é o fator que mais aumenta o custo de um
 *       ataque de força bruta.</li>
 *   <li><b>Sem regras de composição</b> ("uma maiúscula, um número e um símbolo"): segundo o
 *       NIST, elas levam a padrões previsíveis ({@code Senha@123}) e não aumentam a segurança de
 *       forma relevante. O que NÃO é verdade: "senha com símbolo é sempre mais forte".</li>
 *   <li><b>Máximo de 72 bytes em UTF-8:</b> limite técnico do BCrypt, que ignora tudo depois do
 *       byte 72 (e o Spring Security 6.3+ rejeita senhas maiores com erro). São bytes e não
 *       caracteres, porque um caractere acentuado ocupa 2 bytes. Validar aqui transforma um
 *       potencial HTTP 500 em um 400 explicativo.</li>
 * </ul>
 *
 * <h2>Duas fábricas: {@link #forRegistration} e {@link #forAuthentication}</h2>
 * A política vale apenas para senhas <i>novas</i>. No login aceitamos qualquer valor não vazio:
 * se a política mudar amanhã (mínimo de 12, por exemplo), quem cadastrou uma senha de 8
 * caracteres ontem precisa continuar conseguindo entrar.
 */
public final class RawPassword {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private final String value;

    private RawPassword(String value) {
        this.value = value;
    }

    /**
     * Cria uma senha nova aplicando a política de senha.
     *
     * @throws DomainValidationException se a senha não atender à política
     */
    public static RawPassword forRegistration(String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank() || value.length() < MIN_LENGTH) {
            throw new DomainValidationException("WEAK_PASSWORD",
                    "A senha deve ter pelo menos %d caracteres.".formatted(MIN_LENGTH));
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new DomainValidationException("PASSWORD_TOO_LONG",
                    "A senha deve ter no máximo %d bytes (caracteres acentuados ocupam 2).".formatted(MAX_BYTES));
        }
        return new RawPassword(value);
    }

    /**
     * Encapsula uma tentativa de login, sem aplicar a política (veja a explicação da classe).
     */
    public static RawPassword forAuthentication(String value) {
        Objects.requireNonNull(value, "value must not be null");
        return new RawPassword(value);
    }

    /** Valor em texto puro. Use apenas para hashing/comparação, nunca para log ou persistência. */
    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return "RawPassword[****]";
    }
}
