package com.matheuslima.logistics.identity.adapter.in.web;

import com.matheuslima.logistics.identity.application.port.in.UserView;
import com.matheuslima.logistics.identity.domain.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação JSON de um usuário. Nunca inclui hash de senha nem outros dados sensíveis.
 *
 * @param id        identificador
 * @param email     e-mail de login
 * @param role      papel ({@code CUSTOMER} ou {@code OPERATOR})
 * @param createdAt data de cadastro (ISO-8601, UTC)
 */
public record UserResponse(UUID id, String email, Role role, Instant createdAt) {

    static UserResponse from(UserView view) {
        return new UserResponse(view.id(), view.email(), view.role(), view.createdAt());
    }
}
