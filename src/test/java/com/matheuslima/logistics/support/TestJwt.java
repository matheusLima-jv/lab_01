package com.matheuslima.logistics.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Atalhos para simular usuários autenticados nos testes de controller.
 *
 * <p>{@code jwt()} do {@code spring-security-test} injeta um {@code JwtAuthenticationToken} direto
 * no contexto de segurança, sem gerar nem assinar um token real. É rápido e deixa o teste focado
 * no controller. A validação real de tokens (assinatura, expiração, emissor) é coberta em
 * {@code SecurityAdaptersTest} e no teste de ponta a ponta.
 *
 * <p>Atenção: o pós-processador {@code jwt()} não passa pelo {@code JwtAuthenticationConverter},
 * então as authorities precisam ser informadas explicitamente, como fazemos aqui.
 */
public final class TestJwt {

    /** Segredo usado apenas nos testes (Base64 de 32 bytes). */
    public static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC0xMjM=";

    private TestJwt() {
    }

    public static RequestPostProcessor customer(UUID userId) {
        return as(userId, "CUSTOMER");
    }

    public static RequestPostProcessor operator(UUID userId) {
        return as(userId, "OPERATOR");
    }

    private static RequestPostProcessor as(UUID userId, String role) {
        return jwt()
                .jwt(token -> token.subject(userId.toString()).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
