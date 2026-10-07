package com.matheuslima.logistics.identity.application;

import com.matheuslima.logistics.shared.application.ApplicationException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;

import java.util.UUID;

/**
 * Usuário inexistente ou não visível para quem consulta (HTTP 404).
 *
 * <p>"Não encontrado" é um conceito da camada de aplicação: o domínio não busca nada, quem busca
 * é o caso de uso, por meio de uma porta.
 */
public class UserNotFoundException extends ApplicationException {

    public UserNotFoundException(UUID id) {
        super(ErrorCategory.NOT_FOUND, "USER_NOT_FOUND", "Usuário %s não encontrado.".formatted(id));
    }
}
