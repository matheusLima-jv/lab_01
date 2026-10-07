package com.matheuslima.logistics.identity.adapter.in.startup;

import com.matheuslima.logistics.identity.application.port.in.RegisterUserCommand;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.UserView;
import com.matheuslima.logistics.identity.domain.Email;
import com.matheuslima.logistics.identity.domain.EmailAlreadyRegisteredException;
import com.matheuslima.logistics.identity.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do runner que cria o operador na subida.
 */
@DisplayName("OperatorBootstrapRunner")
class OperatorBootstrapRunnerTest {

    private final List<RegisterUserCommand> commands = new ArrayList<>();
    private RuntimeException failure;
    private final RegisterUserUseCase registerUser = command -> {
        commands.add(command);
        if (failure != null) {
            throw failure;
        }
        return new UserView(UUID.randomUUID(), command.email(), command.role(), Instant.now());
    };

    private OperatorBootstrapRunner runner(String email, String password) {
        return new OperatorBootstrapRunner(registerUser, new OperatorBootstrapProperties(true, email, password));
    }

    @Test
    @DisplayName("cadastra o operador pelo mesmo caso de uso do cadastro público, com papel OPERATOR")
    void createsOperator() {
        runner("ops@example.com", "senha-forte-ops").run(new DefaultApplicationArguments());

        assertThat(commands).singleElement().satisfies(command -> {
            assertThat(command.email()).isEqualTo("ops@example.com");
            assertThat(command.role()).isEqualTo(Role.OPERATOR);
        });
    }

    @Test
    @DisplayName("é idempotente: se o operador já existe, apenas segue")
    void idempotent() {
        failure = new EmailAlreadyRegisteredException(Email.of("ops@example.com"));

        assertThatCode(() -> runner("ops@example.com", "senha-forte-ops").run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ligado sem credenciais: falha na subida, com mensagem clara")
    void requiresCredentials() {
        assertThatThrownBy(() -> runner(" ", null).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.bootstrap.operator");
        assertThat(commands).isEmpty();
    }

    @Test
    @DisplayName("as propriedades não expõem a senha no toString")
    void propertiesHidePassword() {
        assertThat(new OperatorBootstrapProperties(true, "ops@example.com", "segredo").toString())
                .doesNotContain("segredo");
    }
}
