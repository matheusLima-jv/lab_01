package com.matheuslima.logistics.identity.application;

import com.matheuslima.logistics.identity.application.fake.IdentityFakes.FakeAccessTokenIssuer;
import com.matheuslima.logistics.identity.application.fake.IdentityFakes.FakePasswordHasher;
import com.matheuslima.logistics.identity.application.fake.IdentityFakes.InMemoryUserRepository;
import com.matheuslima.logistics.identity.application.port.in.AuthenticateUserCommand;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserCommand;
import com.matheuslima.logistics.identity.application.port.in.UserView;
import com.matheuslima.logistics.identity.application.service.AuthenticateUserService;
import com.matheuslima.logistics.identity.application.service.GetUserService;
import com.matheuslima.logistics.identity.application.service.RegisterUserService;
import com.matheuslima.logistics.identity.domain.AccessToken;
import com.matheuslima.logistics.identity.domain.EmailAlreadyRegisteredException;
import com.matheuslima.logistics.identity.domain.Role;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shared.application.fake.DirectTransactionRunner;
import com.matheuslima.logistics.shared.domain.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes dos casos de uso de identidade, com fakes em memória.
 *
 * <p>Repare como é simples montar o cenário: {@code new} em tudo, sem Spring, sem banco, sem
 * Docker. É o benefício prático da arquitetura hexagonal: como o núcleo depende só de
 * interfaces (portas), qualquer implementação serve, inclusive uma de 20 linhas em memória.
 */
@DisplayName("Casos de uso de identidade")
class IdentityUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final DirectTransactionRunner transactions = new DirectTransactionRunner();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RegisterUserService register = new RegisterUserService(users, hasher, transactions, clock);
    private final AuthenticateUserService authenticate = new AuthenticateUserService(users, hasher,
            new FakeAccessTokenIssuer(NOW.plusSeconds(1800)), transactions);
    private final GetUserService getUser = new GetUserService(users, transactions);

    @Nested
    @DisplayName("cadastro")
    class Register {

        @Test
        @DisplayName("cadastra com e-mail normalizado, senha com hash e data do relógio")
        void registersUser() {
            UserView view = register.register(new RegisterUserCommand("Ana@Example.com", "senha-forte-1", Role.CUSTOMER));

            assertThat(view.email()).isEqualTo("ana@example.com");
            assertThat(view.role()).isEqualTo(Role.CUSTOMER);
            assertThat(view.createdAt()).isEqualTo(NOW);
            assertThat(users.count()).isEqualTo(1);
            assertThat(users.findByEmail(com.matheuslima.logistics.identity.domain.Email.of("ana@example.com")))
                    .get()
                    .satisfies(user -> assertThat(user.passwordHash().value()).isNotEqualTo("senha-forte-1"));
        }

        @Test
        @DisplayName("rejeita e-mail já cadastrado (comparação sem diferenciar maiúsculas)")
        void rejectsDuplicateEmail() {
            register.register(new RegisterUserCommand("ana@example.com", "senha-forte-1", Role.CUSTOMER));

            assertThatThrownBy(() -> register.register(
                    new RegisterUserCommand("ANA@example.com", "outra-senha-2", Role.CUSTOMER)))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);
            assertThat(users.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("valida a senha antes de qualquer acesso ao repositório")
        void validatesBeforeTouchingRepository() {
            assertThatThrownBy(() -> register.register(new RegisterUserCommand("ana@example.com", "curta", Role.CUSTOMER)))
                    .isInstanceOf(DomainValidationException.class);
            assertThat(transactions.committedCount()).isZero();
        }

        @Test
        @DisplayName("o comando não expõe a senha no toString")
        void commandMasksPassword() {
            assertThat(new RegisterUserCommand("a@b.com", "segredo-123", Role.CUSTOMER).toString())
                    .doesNotContain("segredo-123");
            assertThat(new AuthenticateUserCommand("a@b.com", "segredo-123").toString())
                    .doesNotContain("segredo-123");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("emite token quando e-mail e senha conferem")
        void issuesTokenForValidCredentials() {
            UserView user = register.register(new RegisterUserCommand("ana@example.com", "senha-forte-1", Role.OPERATOR));

            AccessToken token = authenticate.authenticate(new AuthenticateUserCommand(" ANA@example.com ", "senha-forte-1"));

            assertThat(token.value()).isEqualTo("token-for-" + user.id() + "-OPERATOR");
            assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        }

        @Test
        @DisplayName("senha errada gera InvalidCredentialsException")
        void rejectsWrongPassword() {
            register.register(new RegisterUserCommand("ana@example.com", "senha-forte-1", Role.CUSTOMER));

            assertThatThrownBy(() -> authenticate.authenticate(new AuthenticateUserCommand("ana@example.com", "errada")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("E-mail ou senha inválidos.");
        }

        @Test
        @DisplayName("usuário inexistente gera a MESMA exceção e ainda assim compara a senha (timing attack)")
        void unknownUserStillSpendsTimeComparing() {
            int before = hasher.matchesCalls();

            assertThatThrownBy(() -> authenticate.authenticate(
                    new AuthenticateUserCommand("ninguem@example.com", "qualquer-coisa")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("E-mail ou senha inválidos.");
            assertThat(hasher.matchesCalls()).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("e-mail malformado também é tratado como credencial inválida")
        void malformedEmailIsInvalidCredential() {
            assertThatThrownBy(() -> authenticate.authenticate(new AuthenticateUserCommand("nao-e-email", "x")))
                    .isInstanceOf(InvalidCredentialsException.class);
            assertThatThrownBy(() -> authenticate.authenticate(new AuthenticateUserCommand(null, "x")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("consulta de usuário")
    class GetUser {

        @Test
        @DisplayName("a própria pessoa e operadores podem consultar")
        void selfAndOperatorCanRead() {
            UserView ana = register.register(new RegisterUserCommand("ana@example.com", "senha-forte-1", Role.CUSTOMER));

            assertThat(getUser.get(Requester.customer(ana.id()), ana.id())).isEqualTo(ana);
            assertThat(getUser.get(Requester.operatorUser(UUID.randomUUID()), ana.id())).isEqualTo(ana);
        }

        @Test
        @DisplayName("outro cliente recebe 'não encontrado', sem tocar no banco")
        void otherCustomerGetsNotFound() {
            UserView ana = register.register(new RegisterUserCommand("ana@example.com", "senha-forte-1", Role.CUSTOMER));
            int commitsBefore = transactions.committedCount();

            assertThatThrownBy(() -> getUser.get(Requester.customer(UUID.randomUUID()), ana.id()))
                    .isInstanceOf(UserNotFoundException.class);
            assertThat(transactions.committedCount()).isEqualTo(commitsBefore);
        }

        @Test
        @DisplayName("ID inexistente gera 'não encontrado'")
        void unknownId() {
            UUID unknown = UUID.randomUUID();

            assertThatThrownBy(() -> getUser.get(Requester.operatorUser(UUID.randomUUID()), unknown))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(unknown.toString());
        }
    }
}
