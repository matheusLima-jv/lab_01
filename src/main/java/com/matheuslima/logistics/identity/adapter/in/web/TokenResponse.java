package com.matheuslima.logistics.identity.adapter.in.web;

import java.time.Instant;

/**
 * Resposta do login, inspirada no formato do OAuth 2.0 (RFC 6749, seção 5.1).
 *
 * <p>Seguir nomes consagrados ({@code tokenType}, {@code expiresIn}) facilita a vida de quem
 * integra: bibliotecas e desenvolvedores já esperam esses campos.
 *
 * @param accessToken o JWT; enviar em {@code Authorization: Bearer <accessToken>}
 * @param tokenType   sempre {@code Bearer}
 * @param expiresIn   segundos até expirar (relativo, à prova de relógios dessincronizados)
 * @param expiresAt   instante de expiração (absoluto, conveniente para exibição)
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {

    static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "TokenResponse[accessToken=****, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
