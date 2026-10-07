package com.matheuslima.logistics.identity.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Credencial de acesso emitida após uma autenticação bem-sucedida.
 *
 * <p>No contexto de identidade, "token de acesso" é um conceito de negócio (uma credencial
 * temporária concedida a alguém), por isso mora no domínio. O <i>formato</i> (JWT assinado com
 * HS256) é detalhe do adaptador {@code JwtAccessTokenIssuer}: para o domínio, o token é uma
 * String opaca com prazo de validade.
 *
 * @param value     o token serializado (ex.: o JWT compacto {@code xxx.yyy.zzz})
 * @param expiresAt instante a partir do qual o token deixa de ser aceito
 */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public String toString() {
        // Tokens são credenciais ao portador ("bearer"): quem tem o token, tem o acesso.
        // Nunca devem aparecer em logs.
        return "AccessToken[****, expiresAt=" + expiresAt + "]";
    }
}
