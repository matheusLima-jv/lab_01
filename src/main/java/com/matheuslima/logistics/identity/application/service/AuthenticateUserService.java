package com.matheuslima.logistics.identity.application.service;

import com.matheuslima.logistics.identity.application.InvalidCredentialsException;
import com.matheuslima.logistics.identity.application.port.in.AuthenticateUserCommand;
import com.matheuslima.logistics.identity.application.port.in.AuthenticateUserUseCase;
import com.matheuslima.logistics.identity.application.port.out.AccessTokenIssuer;
import com.matheuslima.logistics.identity.application.port.out.PasswordHasher;
import com.matheuslima.logistics.identity.application.port.out.UserRepository;
import com.matheuslima.logistics.identity.domain.AccessToken;
import com.matheuslima.logistics.identity.domain.Email;
import com.matheuslima.logistics.identity.domain.PasswordHash;
import com.matheuslima.logistics.identity.domain.RawPassword;
import com.matheuslima.logistics.identity.domain.User;
import com.matheuslima.logistics.shared.application.TransactionRunner;
import com.matheuslima.logistics.shared.domain.DomainValidationException;

import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso: autenticar (login) e emitir um token de acesso.
 *
 * <h2>Mitigação de <i>timing attack</i></h2>
 * Implementação ingênua: "se o e-mail não existe, responda erro na hora". O problema é que
 * verificar uma senha com BCrypt leva ~100 ms. Respostas de 2 ms ("e-mail não existe") e de
 * 100 ms ("senha errada") revelam, pelo tempo, quais e-mails têm conta, mesmo com a mesma
 * mensagem de erro. Por isso, quando o usuário não existe, comparamos a senha com um hash
 * "isca" ({@link #dummyHash}) e gastamos o mesmo tempo. É o mesmo truque que o
 * {@code DaoAuthenticationProvider} do Spring Security usa internamente.
 *
 * <h2>E-mail malformado também vira "credenciais inválidas"</h2>
 * No cadastro, um e-mail inválido é erro de validação (400). No login, responder "e-mail
 * inválido" seria só mais uma informação de graça. Login tem uma única resposta de falha.
 *
 * <h2>O que este caso de uso não faz (e um sistema real faria)</h2>
 * Limite de tentativas (rate limiting) e bloqueio temporário após N falhas. Ficam para a borda
 * (API Gateway, Bucket4j) e estão listados como próximos passos na documentação.
 */
public final class AuthenticateUserService implements AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer tokenIssuer;
    private final TransactionRunner transactions;

    /** Hash de uma senha qualquer, usado só para gastar o tempo de uma verificação real. */
    private final PasswordHash dummyHash;

    public AuthenticateUserService(UserRepository users, PasswordHasher passwordHasher, AccessTokenIssuer tokenIssuer,
                                   TransactionRunner transactions) {
        this.users = Objects.requireNonNull(users);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
        this.transactions = Objects.requireNonNull(transactions);
        // Calculado uma vez na criação do bean (subida da aplicação), não a cada login.
        this.dummyHash = passwordHasher.hash(RawPassword.forAuthentication("timing-equalization-dummy-password"));
    }

    @Override
    public AccessToken authenticate(AuthenticateUserCommand command) {
        RawPassword password = RawPassword.forAuthentication(command.password());

        Optional<User> user = parseEmail(command.email())
                .flatMap(email -> transactions.inReadOnlyTransaction(() -> users.findByEmail(email)));

        if (user.isEmpty()) {
            passwordHasher.matches(password, dummyHash); // resultado ignorado de propósito: só gasta tempo
            throw new InvalidCredentialsException();
        }
        if (!passwordHasher.matches(password, user.get().passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokenIssuer.issue(user.get());
    }

    /**
     * Converte o texto em {@link Email}. Usar exceção para controle de fluxo costuma ser má
     * prática, mas aqui ela vem do Value Object (que é a fonte da verdade sobre o formato) e o
     * caminho é raro; duplicar a regex só para evitar a exceção seria pior.
     */
    private static Optional<Email> parseEmail(String rawEmail) {
        if (rawEmail == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Email.of(rawEmail));
        } catch (DomainValidationException invalidEmail) {
            return Optional.empty();
        }
    }
}
