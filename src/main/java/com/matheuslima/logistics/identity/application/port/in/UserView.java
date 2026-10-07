package com.matheuslima.logistics.identity.application.port.in;

import com.matheuslima.logistics.identity.domain.Role;
import com.matheuslima.logistics.identity.domain.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Visão somente-leitura de um usuário, devolvida pelos casos de uso (modelo de saída).
 *
 * <h2>Por que não devolver o agregado {@code User} para o controller?</h2>
 * <ul>
 *   <li><b>Encapsulamento:</b> agregados não saem do núcleo. Se o controller recebesse o
 *       {@code User}, poderia chamar métodos de negócio fora de um caso de uso (sem transação,
 *       sem regras de autorização).</li>
 *   <li><b>Segurança:</b> o agregado tem o hash da senha. A view simplesmente não tem esse campo,
 *       então é impossível serializá-lo por engano.</li>
 * </ul>
 */
public record UserView(UUID id, String email, Role role, Instant createdAt) {

    public static UserView from(User user) {
        return new UserView(user.id().value(), user.email().value(), user.role(), user.createdAt());
    }
}
