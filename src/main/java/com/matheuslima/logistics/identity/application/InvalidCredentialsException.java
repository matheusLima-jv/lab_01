package com.matheuslima.logistics.identity.application;

import com.matheuslima.logistics.shared.application.ApplicationException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;

/**
 * E-mail ou senha não conferem (HTTP 401).
 *
 * <h2>Por que a mesma mensagem para "e-mail não existe" e "senha errada"?</h2>
 * Mensagens diferentes permitiriam a um atacante descobrir quais e-mails têm conta (enumeração
 * de usuários) e então concentrar o ataque de senha neles. Uma única mensagem genérica fecha
 * essa porta. O tempo de resposta também é equalizado (ver {@code AuthenticateUserService}).
 */
public class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException() {
        super(ErrorCategory.UNAUTHENTICATED, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
    }
}
