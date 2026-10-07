package com.matheuslima.logistics.identity.application.port.in;

import com.matheuslima.logistics.identity.domain.AccessToken;

/**
 * <b>Porta de entrada</b>: autenticar com e-mail e senha e obter um token de acesso.
 *
 * <h2>Por que não usar o {@code AuthenticationManager} do Spring Security?</h2>
 * Seria uma alternativa válida ({@code DaoAuthenticationProvider} + {@code UserDetailsService}).
 * Optamos por um caso de uso próprio porque:
 * <ul>
 *   <li>a regra de login fica visível e testável sem subir o Spring;</li>
 *   <li>o núcleo não depende de {@code UserDetails}, um tipo que mistura conceitos (flags de
 *       conta expirada, bloqueada...) que este domínio não tem;</li>
 *   <li>a mesma mitigação de <i>timing attack</i> que o Spring faz internamente fica explícita no
 *       código ({@code AuthenticateUserService}), o que é didático.</li>
 * </ul>
 * O Spring Security continua responsável pela parte em que ele é imbatível: <b>validar</b> o
 * token em cada requisição (Resource Server).
 */
public interface AuthenticateUserUseCase {

    /**
     * @throws com.matheuslima.logistics.identity.application.InvalidCredentialsException quando
     *         e-mail ou senha não conferem. A mensagem é a mesma nos dois casos, de propósito.
     */
    AccessToken authenticate(AuthenticateUserCommand command);
}
