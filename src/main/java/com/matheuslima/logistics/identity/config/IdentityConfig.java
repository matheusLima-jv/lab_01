package com.matheuslima.logistics.identity.config;

import com.matheuslima.logistics.identity.adapter.in.startup.OperatorBootstrapProperties;
import com.matheuslima.logistics.identity.application.port.in.AuthenticateUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.GetUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserUseCase;
import com.matheuslima.logistics.identity.application.port.out.AccessTokenIssuer;
import com.matheuslima.logistics.identity.application.port.out.PasswordHasher;
import com.matheuslima.logistics.identity.application.port.out.UserRepository;
import com.matheuslima.logistics.identity.application.service.AuthenticateUserService;
import com.matheuslima.logistics.identity.application.service.GetUserService;
import com.matheuslima.logistics.identity.application.service.RegisterUserService;
import com.matheuslima.logistics.shared.application.TransactionRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * <b>Composition root</b> do contexto de identidade: onde as peças são conectadas.
 *
 * <h2>Por que {@code @Bean} aqui e não {@code @Service} nos serviços?</h2>
 * Os serviços da camada de aplicação são Java puro, sem anotações do Spring (regra verificada pelo
 * ArchUnit). Alguém precisa então dizer ao Spring "crie um {@code RegisterUserService} com estas
 * dependências", e esse alguém é esta classe. Ganhos:
 * <ul>
 *   <li>o núcleo não depende do framework: dá para testá-lo com {@code new} e até trocar de
 *       framework sem reescrevê-lo;</li>
 *   <li>a "fiação" fica explícita e num lugar só: lendo esta classe, você sabe exatamente qual
 *       implementação atende cada porta;</li>
 *   <li>o tipo de retorno é a <b>porta</b> ({@link RegisterUserUseCase}), então quem injeta
 *       depende da abstração e não da implementação.</li>
 * </ul>
 * Os parâmetros dos métodos {@code @Bean} são resolvidos pelo Spring por tipo: o
 * {@link UserRepository} injetado é o {@code UserPersistenceAdapter}, o {@link PasswordHasher} é o
 * {@code SpringSecurityPasswordHasher}, e assim por diante. Todos são singletons: uma instância
 * por aplicação, compartilhada entre requisições, o que é seguro porque os serviços não guardam
 * estado mutável.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OperatorBootstrapProperties.class)
public class IdentityConfig {

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository users, PasswordHasher passwordHasher,
                                            TransactionRunner transactions, Clock clock) {
        return new RegisterUserService(users, passwordHasher, transactions, clock);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(UserRepository users, PasswordHasher passwordHasher,
                                                    AccessTokenIssuer tokenIssuer, TransactionRunner transactions) {
        return new AuthenticateUserService(users, passwordHasher, tokenIssuer, transactions);
    }

    @Bean
    GetUserUseCase getUserUseCase(UserRepository users, TransactionRunner transactions) {
        return new GetUserService(users, transactions);
    }
}
