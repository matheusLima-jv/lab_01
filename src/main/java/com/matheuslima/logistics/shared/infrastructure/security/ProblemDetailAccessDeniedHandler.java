package com.matheuslima.logistics.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Responde 403 (autenticado, mas sem permissão) no formato padrão de erro da API.
 *
 * <p>Mesma técnica de {@link ProblemDetailAuthenticationEntryPoint}: a negativa que acontece no
 * filtro de segurança (regras por URL, como {@code /actuator/**} só para operadores) é delegada
 * ao {@code GlobalExceptionHandler}, que já trata as negativas vindas de {@code @PreAuthorize}.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver exceptionResolver;

    public ProblemDetailAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        exceptionResolver.resolveException(request, response, null, accessDeniedException);
    }
}
