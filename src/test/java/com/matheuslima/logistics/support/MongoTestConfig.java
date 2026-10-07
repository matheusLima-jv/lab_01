package com.matheuslima.logistics.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * MongoDB real em Docker para os testes. Veja a explicação de {@code @ServiceConnection} em
 * {@link PostgresTestConfig}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MongoTestConfig {

    @Bean
    @ServiceConnection
    public MongoDBContainer mongoContainer() {
        return new MongoDBContainer(ContainerImages.MONGO);
    }
}
