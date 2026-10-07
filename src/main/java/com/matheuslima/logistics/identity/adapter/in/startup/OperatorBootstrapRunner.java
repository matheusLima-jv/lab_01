package com.matheuslima.logistics.identity.adapter.in.startup;

import com.matheuslima.logistics.identity.application.port.in.RegisterUserCommand;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserUseCase;
import com.matheuslima.logistics.identity.domain.EmailAlreadyRegisteredException;
import com.matheuslima.logistics.identity.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Cria a conta de operador na subida da aplicação, se ela ainda não existir.
 *
 * <h2>Um {@link ApplicationRunner} também é um adaptador de entrada</h2>
 * Assim como um controller (HTTP) e um listener (fila), este runner <i>dispara</i> um caso de uso,
 * só que o gatilho é a inicialização da aplicação. Por isso mora em {@code adapter.in} e usa a
 * mesma porta do cadastro público ({@link RegisterUserUseCase}): as mesmas regras (e-mail válido,
 * senha forte, hash com BCrypt) valem para qualquer caminho de entrada.
 *
 * <h2>Por que não inserir o operador com SQL numa migração do Flyway?</h2>
 * A migração teria que conter um hash de senha fixo, e essa senha valeria em todos os ambientes,
 * inclusive produção. Aqui, as credenciais vêm de configuração (variáveis de ambiente ou cofre de
 * segredos) e são diferentes em cada ambiente.
 *
 * <h2>Idempotência</h2>
 * A aplicação sobe muitas vezes (deploys, restarts, réplicas). Se o operador já existe, o runner
 * apenas registra isso no log e segue.
 */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap.operator", name = "enabled", havingValue = "true")
public class OperatorBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OperatorBootstrapRunner.class);

    private final RegisterUserUseCase registerUser;
    private final OperatorBootstrapProperties properties;

    public OperatorBootstrapRunner(RegisterUserUseCase registerUser, OperatorBootstrapProperties properties) {
        this.registerUser = registerUser;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            throw new IllegalStateException(
                    "app.bootstrap.operator.email and app.bootstrap.operator.password must be set when enabled");
        }
        try {
            registerUser.register(new RegisterUserCommand(properties.email(), properties.password(), Role.OPERATOR));
            log.info("Bootstrap operator account '{}' created", properties.email());
        } catch (EmailAlreadyRegisteredException alreadyExists) {
            log.info("Bootstrap operator account '{}' already exists; nothing to do", properties.email());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
