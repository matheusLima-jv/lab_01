package com.matheuslima.logistics.shared.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origens de navegador autorizadas a chamar a API ({@code app.security.cors.allowed-origins}).
 *
 * <p>Aceita uma lista separada por vírgulas, inclusive via variável de ambiente
 * ({@code CORS_ALLOWED_ORIGINS=https://app.empresa.com,https://admin.empresa.com}). Nunca use
 * {@code *} em uma API autenticada: qualquer site poderia fazer requisições em nome do usuário
 * a partir do navegador dele.
 *
 * @param allowedOrigins origens exatas (esquema + host + porta)
 */
@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
    }
}
