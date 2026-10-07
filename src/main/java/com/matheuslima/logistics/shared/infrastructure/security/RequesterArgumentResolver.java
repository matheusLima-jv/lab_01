package com.matheuslima.logistics.shared.infrastructure.security;

import com.matheuslima.logistics.shared.application.Requester;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Permite que qualquer endpoint receba {@code Requester requester} como parâmetro, já preenchido
 * a partir do JWT autenticado.
 *
 * <h2>Por que um {@link HandlerMethodArgumentResolver}?</h2>
 * Sem ele, todo controller repetiria: pegar o {@code Authentication}, verificar o tipo, ler o
 * {@code sub}, converter em UUID, checar se tem o papel de operador... Com o resolvedor, essa
 * tradução "HTTP/JWT → conceito da aplicação" é feita em um lugar só e testada uma vez. O
 * controller declara o que precisa ({@code Requester}) e não como obtê-lo. É o mesmo mecanismo
 * que o Spring usa para {@code @PathVariable} e {@code @RequestBody}.
 *
 * <p>Fica no pacote de segurança porque é exatamente a fronteira entre o mundo do Spring
 * Security ({@link JwtAuthenticationToken}) e o mundo da aplicação ({@link Requester}).
 */
public class RequesterArgumentResolver implements HandlerMethodArgumentResolver {

    static final String OPERATOR_AUTHORITY = "ROLE_OPERATOR";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Requester.class.equals(parameter.getParameterType());
    }

    @Override
    public Requester resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                     NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            // Só acontece se um endpoint público declarar Requester por engano: falha alto.
            throw new AuthenticationCredentialsNotFoundException("A JWT-authenticated request is required");
        }
        boolean operator = jwtAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(OPERATOR_AUTHORITY::equals);
        return new Requester(parseSubject(jwtAuthentication.getToken().getSubject()), operator);
    }

    private static UUID parseSubject(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException invalidSubject) {
            // Token com assinatura válida mas "sub" fora do padrão: tratamos como token inválido (401).
            throw new InvalidBearerTokenException("Token subject is not a valid user id");
        }
    }
}
