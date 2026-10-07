package com.matheuslima.logistics.support;

import com.matheuslima.logistics.identity.adapter.in.web.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Base dos testes de <b>ponta a ponta</b>: a aplicação inteira, num servidor HTTP real, com
 * PostgreSQL, MongoDB e RabbitMQ reais (Testcontainers) e a ViaCEP simulada (WireMock).
 *
 * <h2>Topo da pirâmide de testes</h2>
 * São os testes mais lentos e mais completos. Provam que as peças, já testadas isoladamente,
 * funcionam <i>juntas</i>: configuração, beans, segurança, transações, outbox, broker, listener,
 * MongoDB. Por isso são poucos e cobrem jornadas, e não cada regra (essas estão nos testes de
 * unidade, muito mais rápidos).
 *
 * <h2>Desempenho: um contexto só</h2>
 * Todas as classes que estendem esta base têm exatamente a mesma configuração, então o Spring
 * reaproveita o mesmo contexto (e os mesmos containers) entre elas. Por consequência, os testes
 * não podem depender de banco vazio: cada um cria seus próprios dados (e-mails e IDs únicos).
 *
 * <p>{@code webEnvironment = RANDOM_PORT} sobe o Tomcat de verdade numa porta livre, e o
 * {@link RestTestClient} (Spring Framework 7) faz requisições HTTP reais contra ele.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, MongoTestConfig.class, RabbitTestConfig.class})
public abstract class AbstractIntegrationTest {

    protected static final String OPERATOR_EMAIL = "operador@test.local";
    protected static final String OPERATOR_PASSWORD = "operador-test-123";

    @LocalServerPort
    private int port;

    protected RestTestClient http;

    /** Aponta a integração ViaCEP para o WireMock antes de o contexto subir. */
    @DynamicPropertySource
    static void externalServices(DynamicPropertyRegistry registry) {
        registry.add("app.integrations.viacep.base-url", ViaCepStub::baseUrl);
    }

    @BeforeEach
    void setUpHttpClient() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Cadastra um cliente com e-mail único e devolve o token dele. */
    protected String newCustomerToken() {
        String email = "cliente-" + UUID.randomUUID() + "@example.com";
        http.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "senha-forte-123"))
                .exchange()
                .expectStatus().isCreated();
        return login(email, "senha-forte-123");
    }

    /** Token do operador criado pelo bootstrap (perfil test). */
    protected String operatorToken() {
        return login(OPERATOR_EMAIL, OPERATOR_PASSWORD);
    }

    protected String login(String email, String password) {
        return http.post().uri("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
