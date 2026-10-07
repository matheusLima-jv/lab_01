package com.matheuslima.logistics.identity.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/auth/token} (login).
 *
 * <p>Repare que a validação é mínima (não vazio e com tamanho máximo, para não processar textos
 * gigantes). Validar o <i>formato</i> do e-mail aqui daria uma resposta diferente para "e-mail
 * malformado" e "senha errada"; no login, toda falha deve ter a mesma resposta.
 *
 * @param email    e-mail de login
 * @param password senha
 */
public record TokenRequest(
        @Schema(example = "ana@example.com")
        @NotBlank @Size(max = 254)
        String email,

        @Schema(example = "senha-forte-123")
        @NotBlank @Size(max = 200)
        String password) {

    @Override
    public String toString() {
        return "TokenRequest[email=" + email + ", password=****]";
    }
}
