package com.matheuslima.logistics.identity.application.service;

import com.matheuslima.logistics.identity.application.port.in.RegisterUserCommand;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.UserView;
import com.matheuslima.logistics.identity.application.port.out.PasswordHasher;
import com.matheuslima.logistics.identity.application.port.out.UserRepository;
import com.matheuslima.logistics.identity.domain.Email;
import com.matheuslima.logistics.identity.domain.EmailAlreadyRegisteredException;
import com.matheuslima.logistics.identity.domain.PasswordHash;
import com.matheuslima.logistics.identity.domain.RawPassword;
import com.matheuslima.logistics.identity.domain.User;
import com.matheuslima.logistics.shared.application.TransactionRunner;

import java.time.Clock;
import java.util.Objects;

/**
 * Caso de uso: cadastrar usuário (um <b>Application Service</b>).
 *
 * <h2>Papel de um Application Service</h2>
 * Ele <i>orquestra</i>, não decide regras de negócio: converte a entrada em objetos de domínio
 * (que se validam sozinhos), chama portas de saída, delimita a transação e devolve uma view.
 * As regras ("e-mail válido", "senha forte") estão nos Value Objects; a regra "e-mail único" é
 * aplicada aqui porque depende de consultar o repositório, coisa que o domínio não faz.
 *
 * <h2>Por que esta classe não tem {@code @Service}?</h2>
 * A camada de aplicação não depende do Spring (regra do ArchUnit). O objeto é criado como bean
 * por um método {@code @Bean} em {@code IdentityConfig}, ou seja, quem conhece o Spring é a
 * configuração, não o caso de uso. Bônus: o teste unitário cria o serviço com {@code new}, sem
 * contexto Spring.
 *
 * <h2>Por que o hash é calculado ANTES de abrir a transação?</h2>
 * BCrypt é lento de propósito (~100 ms de CPU, para encarecer força bruta). Dentro da transação,
 * uma conexão do pool ficaria presa esse tempo todo sem fazer nada no banco. Fora dela, a
 * conexão só é usada durante o SELECT + INSERT, poucos milissegundos.
 */
public final class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TransactionRunner transactions;
    private final Clock clock;

    /**
     * Injeção pelo construtor: dependências explícitas, campos {@code final} (imutáveis) e
     * impossível criar o objeto sem tudo de que ele precisa. É o padrão recomendado pelo
     * próprio time do Spring, em vez de {@code @Autowired} em campos.
     */
    public RegisterUserService(UserRepository users, PasswordHasher passwordHasher, TransactionRunner transactions,
                               Clock clock) {
        this.users = Objects.requireNonNull(users);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public UserView register(RegisterUserCommand command) {
        // 1) Entrada -> Value Objects: se algo for inválido, falha aqui, antes de qualquer I/O.
        Email email = Email.of(command.email());
        RawPassword password = RawPassword.forRegistration(command.password());

        // 2) Trabalho caro de CPU fora da transação (ver Javadoc da classe).
        PasswordHash hash = passwordHasher.hash(password);

        // 3) Transação curta: checagem de unicidade + inserção.
        return transactions.inTransaction(() -> {
            if (users.existsByEmail(email)) {
                throw new EmailAlreadyRegisteredException(email);
            }
            User user = User.register(email, hash, command.role(), clock.instant());
            users.save(user);
            return UserView.from(user);
        });
    }
}
