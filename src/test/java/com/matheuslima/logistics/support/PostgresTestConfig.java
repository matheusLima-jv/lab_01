package com.matheuslima.logistics.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL real em Docker para os testes, ligado ao Spring com {@code @ServiceConnection}.
 *
 * <h2>Como o {@code @ServiceConnection} funciona</h2>
 * O Spring Boot reconhece o tipo do container e cria sozinho os detalhes de conexão
 * ({@code JdbcConnectionDetails}) com a URL, o usuário e a senha do container, que sobe numa
 * porta aleatória. Não é preciso nenhum {@code @DynamicPropertySource} manual. O Spring também
 * gerencia o ciclo de vida: inicia o container antes do contexto e o para quando o contexto
 * fecha.
 *
 * <h2>Por que não H2 em memória?</h2>
 * H2 em "modo PostgreSQL" não tem {@code JSONB}, {@code FOR UPDATE SKIP LOCKED}, índices
 * parciais nem o operador de regex {@code ~} usados nas migrações. Testar em H2 seria testar
 * outro banco: verde no teste, vermelho em produção.
 *
 * <h2>Reaproveitamento</h2>
 * O Spring guarda em cache os contextos de teste com a mesma configuração, então os containers
 * sobem uma vez por configuração distinta, e não uma vez por classe de teste.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(ContainerImages.POSTGRES);
    }
}
