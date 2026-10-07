package com.matheuslima.logistics.support;

import org.testcontainers.utility.DockerImageName;

/**
 * Imagens Docker usadas nos testes de integração.
 *
 * <p>São as <b>mesmas versões</b> do {@code docker-compose.yml}. Testar contra a mesma versão que
 * roda em produção é o ponto central do Testcontainers: um teste verde com PostgreSQL 17 não
 * garante nada sobre um PostgreSQL 12. Ao atualizar uma versão, atualize os dois lugares.
 */
public final class ContainerImages {

    public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:17-alpine");
    public static final DockerImageName MONGO = DockerImageName.parse("mongo:8.0");
    public static final DockerImageName RABBITMQ = DockerImageName.parse("rabbitmq:4.1-management-alpine");

    private ContainerImages() {
    }
}
