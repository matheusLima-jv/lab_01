package com.matheuslima.logistics;

import com.matheuslima.logistics.support.MongoTestConfig;
import com.matheuslima.logistics.support.PostgresTestConfig;
import com.matheuslima.logistics.support.RabbitTestConfig;
import org.springframework.boot.SpringApplication;

/**
 * Sobe a aplicação completa <b>com a infraestrutura em containers</b>, sem docker compose e sem
 * instalar nada.
 *
 * <p>Execute pela IDE (é um {@code main} comum) ou com {@code ./mvnw spring-boot:test-run}. O Spring
 * Boot reutiliza as mesmas configurações de Testcontainers dos testes de integração: PostgreSQL,
 * MongoDB e RabbitMQ sobem em portas aleatórias e são conectados à aplicação automaticamente
 * ({@code @ServiceConnection}). O perfil {@code local} fornece o segredo JWT de desenvolvimento e
 * cria o operador {@code operador@logistics.local / operador-local-123}.
 *
 * <p>Fica em {@code src/test} porque depende de bibliotecas de teste (Testcontainers), que não
 * devem ir para o jar de produção.
 */
public class TestLogisticsApplication {

    public static void main(String[] args) {
        SpringApplication.from(LogisticsApplication::main)
                .with(PostgresTestConfig.class, MongoTestConfig.class, RabbitTestConfig.class)
                .withAdditionalProfiles("local")
                .run(args);
    }
}
