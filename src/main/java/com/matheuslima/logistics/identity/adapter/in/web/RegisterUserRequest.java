package com.matheuslima.logistics.identity.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/users}.
 *
 * <h2>DTO de borda: por que não usar o Command direto como corpo?</h2>
 * Este record pertence ao contrato HTTP: tem anotações de validação (mensagens por campo para
 * quem consome a API) e de documentação ({@code @Schema}, exemplos no Swagger). Repare também no
 * que ele <b>não</b> tem: o papel ({@code role}). O cliente não escolhe o próprio papel; o
 * controller sempre cadastra {@code CUSTOMER}. Se o corpo fosse o Command, bastaria enviar
 * {@code "role": "OPERATOR"} para escalar privilégio (ataque de <i>mass assignment</i>).
 *
 * @param email    e-mail de login
 * @param password senha (8 caracteres no mínimo)
 */
public record RegisterUserRequest(
        @Schema(example = "ana@example.com")
        @NotBlank @Email @Size(max = 254)
        String email,

        @Schema(example = "senha-forte-123", minLength = 8, maxLength = 72)
        @NotBlank @Size(min = 8, max = 72)
        String password) {

    @Override
    public String toString() {
        return "RegisterUserRequest[email=" + email + ", password=****]";
    }
}
