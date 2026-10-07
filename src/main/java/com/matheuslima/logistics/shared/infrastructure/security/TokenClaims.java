package com.matheuslima.logistics.shared.infrastructure.security;

/**
 * Nomes das claims customizadas do JWT, compartilhados entre quem <b>emite</b> o token
 * ({@code JwtAccessTokenIssuer}, contexto de identidade) e quem <b>lê</b>
 * ({@code SecurityConfig}, {@code RequesterArgumentResolver}).
 *
 * <p>Claims padrão (RFC 7519) não precisam estar aqui: {@code sub} (dono do token), {@code iss}
 * (emissor), {@code iat}/{@code exp} (emissão/expiração) e {@code jti} (ID do token) já têm
 * constantes no Spring Security ({@code JwtClaimNames}).
 */
public final class TokenClaims {

    /** Lista de papéis do usuário, ex.: {@code ["CUSTOMER"]}. */
    public static final String ROLES = "roles";

    private TokenClaims() {
    }
}
