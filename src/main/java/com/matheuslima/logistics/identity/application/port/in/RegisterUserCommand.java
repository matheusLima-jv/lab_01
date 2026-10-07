package com.matheuslima.logistics.identity.application.port.in;

import com.matheuslima.logistics.identity.domain.Role;

import java.util.Objects;

/**
 * Dados de entrada do caso de uso de cadastro (padrão <b>Command</b>).
 *
 * <h2>Por que um Command em vez de passar o DTO HTTP direto?</h2>
 * O DTO HTTP ({@code RegisterUserRequest}) pertence ao adaptador web: tem anotações de validação
 * e de OpenAPI e pode mudar por motivos de API (renomear campo, versão v2). O Command pertence ao
 * núcleo e muda só quando o caso de uso muda. Se o núcleo recebesse o DTO, toda mudança na API
 * vazaria para dentro, e outros adaptadores (o bootstrap de operador, por exemplo) teriam que
 * fingir ser uma requisição HTTP.
 *
 * <p>Os campos são tipos simples (String) de propósito: a conversão para Value Objects
 * ({@code Email}, {@code RawPassword}) acontece no serviço, onde as regras de domínio são
 * aplicadas.
 *
 * @param email    e-mail informado
 * @param password senha em texto puro
 * @param role     papel a conceder. O endpoint público sempre envia {@link Role#CUSTOMER}:
 *                 o cliente da API nunca escolhe o próprio papel.
 */
public record RegisterUserCommand(String email, String password, Role role) {

    public RegisterUserCommand {
        Objects.requireNonNull(role, "role must not be null");
    }

    /**
     * Records geram um {@code toString} com todos os campos. Sobrescrevemos para que a senha
     * nunca apareça em logs ou mensagens de erro.
     */
    @Override
    public String toString() {
        return "RegisterUserCommand[email=" + email + ", password=****, role=" + role + "]";
    }
}
