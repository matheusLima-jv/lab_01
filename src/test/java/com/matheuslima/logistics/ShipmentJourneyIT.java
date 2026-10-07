package com.matheuslima.logistics;

import com.matheuslima.logistics.shared.infrastructure.outbox.OutboxMessageRepository;
import com.matheuslima.logistics.shipping.adapter.in.web.ShipmentResponse;
import com.matheuslima.logistics.shipping.adapter.in.web.TrackingHistoryResponse;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;
import com.matheuslima.logistics.support.AbstractIntegrationTest;
import com.matheuslima.logistics.support.ViaCepStub;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Jornadas completas pela API, com toda a infraestrutura real.
 *
 * <p>O passo mais interessante é o rastreamento: depois do despacho e da entrega, o teste
 * <b>espera</b> ({@code await}) o histórico aparecer, porque ele é alimentado de forma assíncrona
 * (outbox → relay → RabbitMQ → listener → MongoDB). Awaitility repete a verificação até ela passar
 * ou o tempo acabar. É a forma correta de testar consistência eventual, em vez de um
 * {@code Thread.sleep} chutado, que deixa o teste lento quando sobra tempo e instável quando
 * falta.
 */
@DisplayName("Jornada de envio (ponta a ponta)")
class ShipmentJourneyIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxMessageRepository outbox;

    @BeforeAll
    static void stubViaCep() {
        ViaCepStub.stubAddress("01001000", "Praça da Sé", "Sé", "São Paulo", "SP");
        ViaCepStub.stubAddress("20040020", "Praça Pio X", "Centro", "Rio de Janeiro", "RJ");
        ViaCepStub.stubAddress("69005010", "Rua Barão de São Domingos", "Centro", "Manaus", "AM");
        ViaCepStub.stubAddress("30130010", "Praça Sete de Setembro", "Centro", "Belo Horizonte", "MG");
        ViaCepStub.stubAddress("80010000", "Rua José Loureiro", "Centro", "Curitiba", "PR");
        ViaCepStub.stubNotFound("99999999");
        ViaCepStub.stubServerError("88888888");
    }

    private static Map<String, Object> shipmentBody(String originCep, String destinationCep, int grams) {
        return Map.of(
                "origin", Map.of("postalCode", originCep, "number", "100"),
                "destination", Map.of("postalCode", destinationCep, "number", "10"),
                "weightGrams", grams);
    }

    private ShipmentResponse createShipment(String token, Map<String, Object> body) {
        return http.post().uri("/api/v1/shipments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ShipmentResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    @DisplayName("cadastro → login → envio → despacho → entrega → rastreamento assíncrono")
    void fullJourney() {
        String customer = newCustomerToken();
        String operator = operatorToken();

        // 1. Criação: endereços enriquecidos pela "ViaCEP" e frete calculado (SP → AM, 30 kg).
        ShipmentResponse created = createShipment(customer, shipmentBody("01001-000", "69005010", 30_000));
        assertThat(created.status()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(created.origin().street()).isEqualTo("Praça da Sé");
        assertThat(created.destination().city()).isEqualTo("Manaus");
        assertThat(created.freight().amount()).isEqualByComparingTo(new BigDecimal("110.00"));

        // 2. Ciclo de vida pelo operador.
        http.post().uri("/api/v1/shipments/{id}/dispatch", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("IN_TRANSIT");
        http.post().uri("/api/v1/shipments/{id}/deliver", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("DELIVERED");

        // 3. O cliente vê o estado final...
        http.get().uri("/api/v1/shipments/{id}", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("DELIVERED");

        // 4. ...e, alguns instantes depois, a linha do tempo completa (consistência eventual).
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            TrackingHistoryResponse history = http.get().uri("/api/v1/shipments/{id}/tracking", created.id())
                    .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(TrackingHistoryResponse.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(history.events())
                    .extracting(TrackingHistoryResponse.EventResponse::status)
                    .containsExactly(ShipmentStatus.CREATED, ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED);
            assertThat(history.events().getFirst().description())
                    .isEqualTo("Envio registrado: São Paulo/SP → Manaus/AM");
        });

        // 5. Nenhuma mensagem deste envio ficou presa no outbox.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outbox.findAll()).filteredOn(m -> m.getAggregateId().equals(created.id()))
                        .hasSize(3)
                        .allSatisfy(m -> assertThat(m.getPublishedAt()).isNotNull()));
    }

    @Test
    @DisplayName("o cache evita consultar a ViaCEP de novo para o mesmo CEP")
    void postalCodeCache() {
        String customer = newCustomerToken();

        createShipment(customer, shipmentBody("30130-010", "80010-000", 500));
        createShipment(customer, shipmentBody("30130010", "80010000", 800));

        assertThat(ViaCepStub.requestsFor("30130010")).isEqualTo(1);
        assertThat(ViaCepStub.requestsFor("80010000")).isEqualTo(1);
    }

    @Test
    @DisplayName("CEP inexistente → 422; ViaCEP com erro → 503 com Retry-After")
    void externalLookupFailures() {
        String customer = newCustomerToken();

        http.post().uri("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).body(shipmentBody("01001000", "99999999", 500))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("POSTAL_CODE_NOT_FOUND");

        http.post().uri("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).body(shipmentBody("88888888", "01001000", 500))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "30")
                .expectBody().jsonPath("$.code").isEqualTo("EXTERNAL_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("isolamento entre clientes: envio de outra pessoa é 404, e a listagem só traz os próprios")
    void customersAreIsolated() {
        String ana = newCustomerToken();
        String bruno = newCustomerToken();
        ShipmentResponse anas = createShipment(ana, shipmentBody("01001000", "20040020", 1_000));

        http.get().uri("/api/v1/shipments/{id}", anas.id()).header(HttpHeaders.AUTHORIZATION, bearer(bruno))
                .exchange().expectStatus().isNotFound();
        http.post().uri("/api/v1/shipments/{id}/cancel", anas.id()).header(HttpHeaders.AUTHORIZATION, bearer(bruno))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("reason", "tentativa indevida"))
                .exchange().expectStatus().isNotFound();
        http.get().uri("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(bruno))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(0);
        http.get().uri("/api/v1/shipments?status=CREATED").header(HttpHeaders.AUTHORIZATION, bearer(operatorToken()))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.content[?(@.id == '%s')]".formatted(anas.id())).exists();
    }

    @Test
    @DisplayName("cliente cancela o próprio envio; depois disso não dá para despachar (409)")
    void cancelThenConflict() {
        String customer = newCustomerToken();
        ShipmentResponse created = createShipment(customer, shipmentBody("01001000", "20040020", 1_000));

        http.post().uri("/api/v1/shipments/{id}/cancel", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("reason", "Comprei errado"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED")
                .jsonPath("$.cancellationReason").isEqualTo("Comprei errado");

        http.post().uri("/api/v1/shipments/{id}/dispatch", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(operatorToken()))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("SHIPMENT_INVALID_STATUS_TRANSITION");
    }

    @Test
    @DisplayName("segurança de ponta a ponta: token real, token adulterado, papéis e Actuator")
    void endToEndSecurity() {
        String customer = newCustomerToken();

        http.get().uri("/api/v1/shipments").exchange().expectStatus().isUnauthorized();
        http.get().uri("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(customer + "x"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().value(HttpHeaders.WWW_AUTHENTICATE, value -> assertThat(value).contains("invalid_token"));
        http.get().uri("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .exchange().expectStatus().isForbidden();
        http.get().uri("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(operatorToken()))
                .exchange().expectStatus().isOk();
        http.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        http.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.info.title").isEqualTo("Logistics API");
    }
}
