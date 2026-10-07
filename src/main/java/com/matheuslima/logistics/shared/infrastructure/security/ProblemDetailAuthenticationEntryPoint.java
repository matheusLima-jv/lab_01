package com.matheuslima.logistics.shared.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Responde 401 (não autenticado) no mesmo formato de erro do resto da API.
 *
 * <h2>O problema</h2>
 * Falhas de autenticação acontecem nos <i>filtros</i> do Spring Security, antes de a requisição
 * chegar ao Spring MVC. Por isso o {@code @RestControllerAdvice} não as vê, e o padrão do
 * Resource Server é responder 401 com corpo vazio. Funciona, mas quebra a consistência: todo
 * erro da API é um Problem Details, menos o 401.
 *
 * <h2>A solução: delegar ao {@link HandlerExceptionResolver}</h2>
 * <ol>
 *   <li>O {@link BearerTokenAuthenticationEntryPoint} padrão define o status 401 e o header
 *       {@code WWW-Authenticate} no formato da RFC 6750 (com o motivo, ex.:
 *       {@code error="invalid_token"}).</li>
 *   <li>Em seguida a exceção é entregue ao resolvedor de exceções do Spring MVC, que a encaminha
 *       ao {@code GlobalExceptionHandler}. Resultado: um único lugar define o formato de todos os
 *       erros.</li>
 * </ol>
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final HandlerExceptionResolver exceptionResolver;

    public ProblemDetailAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, authException);
        exceptionResolver.resolveException(request, response, null, authException);
    }
}
