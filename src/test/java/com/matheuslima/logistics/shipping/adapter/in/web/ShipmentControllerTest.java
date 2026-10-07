package com.matheuslima.logistics.shipping.adapter.in.web;

import com.matheuslima.logistics.shared.application.ConcurrentUpdateException;
import com.matheuslima.logistics.shared.application.ExternalServiceUnavailableException;
import com.matheuslima.logistics.shared.application.PageResult;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shipping.ShippingFixtures;
import com.matheuslima.logistics.shipping.application.PostalCodeNotFoundException;
import com.matheuslima.logistics.shipping.application.ShipmentNotFoundException;
import com.matheuslima.logistics.shipping.application.port.in.ChangeShipmentStatusCommand;
import com.matheuslima.logistics.shipping.application.port.in.ChangeShipmentStatusUseCase;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentCommand;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentUseCase;
import com.matheuslima.logistics.shipping.application.port.in.GetShipmentUseCase;
import com.matheuslima.logistics.shipping.application.port.in.GetTrackingHistoryUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ListShipmentsQuery;
import com.matheuslima.logistics.shipping.application.port.in.ListShipmentsUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ShipmentView;
import com.matheuslima.logistics.shipping.application.port.in.StatusChange;
import com.matheuslima.logistics.shipping.application.port.in.TrackingEntryView;
import com.matheuslima.logistics.shipping.domain.InvalidShipmentStatusTransitionException;
import com.matheuslima.logistics.shipping.domain.PostalCode;
import com.matheuslima.logistics.shipping.domain.Shipment;
import com.matheuslima.logistics.shipping.domain.ShipmentId;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;
import com.matheuslima.logistics.support.TestJwt;
import com.matheuslima.logistics.support.WebAdapterTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.matheuslima.logistics.shipping.ShippingFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do contrato HTTP de {@link ShipmentController}.
 *
 * <p>Os casos de uso são mocks (Mockito): aqui não importa <i>como</i> o envio é criado, e sim se
 * o controller traduz corretamente HTTP ↔ caso de uso. É o lugar certo para o estilo
 * "mockista": a interação com a porta de entrada é exatamente o que queremos verificar.
 *
 * <p>Cobre também o comportamento transversal que passa por este controller: segurança (401/403),
 * validação (400), tradução de exceções de negócio em status e o correlation ID.
 */
@WebAdapterTest(ShipmentController.class)
@DisplayName("ShipmentController (contrato HTTP)")
class ShipmentControllerTest {

    private static final String VALID_BODY = """
            {
              "origin": {"postalCode": "01001-000", "number": "100", "complement": "Sala 1"},
              "destination": {"postalCode": "20040020", "number": "10"},
              "weightGrams": 1500
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateShipmentUseCase createShipment;
    @MockitoBean
    private ChangeShipmentStatusUseCase changeShipmentStatus;
    @MockitoBean
    private GetShipmentUseCase getShipment;
    @MockitoBean
    private ListShipmentsUseCase listShipments;
    @MockitoBean
    private GetTrackingHistoryUseCase getTrackingHistory;

    private final UUID customerId = UUID.randomUUID();
    private final ShipmentView view = sampleView();

    private ShipmentView sampleView() {
        Shipment shipment = ShippingFixtures.newShipment(com.matheuslima.logistics.shipping.domain.CustomerId.of(customerId));
        return ShipmentView.from(shipment);
    }

    @Nested
    @DisplayName("POST /api/v1/shipments")
    class Create {

        @Test
        @DisplayName("201 com Location relativo e corpo; o dono é quem está autenticado")
        void createsShipment() throws Exception {
            given(createShipment.create(any())).willReturn(view);

            mvc.perform(post("/api/v1/shipments").with(TestJwt.customer(customerId))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/shipments/" + view.id()))
                    .andExpect(jsonPath("$.id").value(view.id().toString()))
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.origin.city").value("São Paulo"))
                    .andExpect(jsonPath("$.freight.amount").value(30.00))
                    .andExpect(jsonPath("$.freight.currency").value("BRL"))
                    .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"));

            ArgumentCaptor<CreateShipmentCommand> command = ArgumentCaptor.forClass(CreateShipmentCommand.class);
            verify(createShipment).create(command.capture());
            assertThat(command.getValue().requester()).isEqualTo(Requester.customer(customerId));
            assertThat(command.getValue().origin().postalCode()).isEqualTo("01001-000");
            assertThat(command.getValue().origin().complement()).isEqualTo("Sala 1");
            assertThat(command.getValue().destination().number()).isEqualTo("10");
            assertThat(command.getValue().weightGrams()).isEqualTo(1500);
        }

        @Test
        @DisplayName("400 com a lista de campos inválidos, sem chamar o caso de uso")
        void validationErrors() throws Exception {
            mvc.perform(post("/api/v1/shipments").with(TestJwt.customer(customerId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"origin": {"postalCode": "123", "number": ""}, "weightGrams": 0}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.type").value("https://logistics.example.com/problems/validation-failed"))
                    .andExpect(jsonPath("$.errors", hasSize(4)))
                    .andExpect(jsonPath("$.errors[*].field")
                            .value(org.hamcrest.Matchers.containsInAnyOrder(
                                    "origin.postalCode", "origin.number", "destination", "weightGrams")));
            verifyNoInteractions(createShipment);
        }

        @Test
        @DisplayName("400 MALFORMED_REQUEST para JSON quebrado")
        void malformedJson() throws Exception {
            mvc.perform(post("/api/v1/shipments").with(TestJwt.customer(customerId))
                            .contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }

        @Test
        @DisplayName("401 sem token, com WWW-Authenticate e corpo no formato padrão")
        void requiresAuthentication() throws Exception {
            mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.status").value(401));
            verifyNoInteractions(createShipment);
        }

        @Test
        @DisplayName("422 quando o CEP não existe")
        void unknownPostalCode() throws Exception {
            given(createShipment.create(any()))
                    .willThrow(new PostalCodeNotFoundException(PostalCode.of("99999999"), "destino"));

            mvc.perform(post("/api/v1/shipments").with(TestJwt.customer(customerId))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("POSTAL_CODE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("CEP de destino não encontrado: 99999-999."));
        }

        @Test
        @DisplayName("503 com Retry-After quando a ViaCEP está fora do ar")
        void dependencyUnavailable() throws Exception {
            given(createShipment.create(any()))
                    .willThrow(new ExternalServiceUnavailableException("ViaCEP", new RuntimeException("timeout")));

            mvc.perform(post("/api/v1/shipments").with(TestJwt.customer(customerId))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.detail", not(containsString("timeout"))));
        }
    }

    @Nested
    @DisplayName("consultas")
    class Queries {

        @Test
        @DisplayName("GET /{id}: 200 para o dono; 404 propagado do caso de uso")
        void getById() throws Exception {
            given(getShipment.get(Requester.customer(customerId), view.id())).willReturn(view);
            UUID unknown = UUID.randomUUID();
            given(getShipment.get(Requester.customer(customerId), unknown))
                    .willThrow(new ShipmentNotFoundException(ShipmentId.of(unknown)));

            mvc.perform(get("/api/v1/shipments/{id}", view.id()).with(TestJwt.customer(customerId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()));
            mvc.perform(get("/api/v1/shipments/{id}", unknown).with(TestJwt.customer(customerId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_NOT_FOUND"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/shipments/" + unknown));
        }

        @Test
        @DisplayName("GET /{id} com UUID inválido: 400 INVALID_PARAMETER")
        void invalidUuid() throws Exception {
            mvc.perform(get("/api/v1/shipments/{id}", "nao-e-uuid").with(TestJwt.customer(customerId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }

        @Test
        @DisplayName("GET lista: repassa filtro e paginação e devolve o envelope de página")
        void list() throws Exception {
            given(listShipments.list(any())).willReturn(new PageResult<>(List.of(view), 1, 5, 6));

            mvc.perform(get("/api/v1/shipments").param("status", "CREATED").param("page", "1").param("size", "5")
                            .with(TestJwt.customer(customerId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(5))
                    .andExpect(jsonPath("$.totalElements").value(6))
                    .andExpect(jsonPath("$.totalPages").value(2));

            ArgumentCaptor<ListShipmentsQuery> query = ArgumentCaptor.forClass(ListShipmentsQuery.class);
            verify(listShipments).list(query.capture());
            assertThat(query.getValue().status()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(query.getValue().page().page()).isEqualTo(1);
            assertThat(query.getValue().page().size()).isEqualTo(5);
        }

        @Test
        @DisplayName("GET lista: size acima de 100 e status desconhecido viram 400")
        void listValidation() throws Exception {
            mvc.perform(get("/api/v1/shipments").param("size", "500").with(TestJwt.customer(customerId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("size"));
            mvc.perform(get("/api/v1/shipments").param("status", "VOANDO").with(TestJwt.customer(customerId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            verifyNoInteractions(listShipments);
        }

        @Test
        @DisplayName("GET /{id}/tracking: linha do tempo")
        void tracking() throws Exception {
            UUID eventId = UUID.randomUUID();
            given(getTrackingHistory.history(Requester.customer(customerId), view.id())).willReturn(List.of(
                    new TrackingEntryView(eventId, ShipmentStatus.CREATED, "Envio registrado", NOW)));

            mvc.perform(get("/api/v1/shipments/{id}/tracking", view.id()).with(TestJwt.customer(customerId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shipmentId").value(view.id().toString()))
                    .andExpect(jsonPath("$.events[0].eventId").value(eventId.toString()))
                    .andExpect(jsonPath("$.events[0].status").value("CREATED"))
                    .andExpect(jsonPath("$.events[0].occurredAt").value("2026-01-15T10:00:00Z"));
        }
    }

    @Nested
    @DisplayName("transições de status")
    class Transitions {

        @Test
        @DisplayName("cliente não despacha nem entrega (403 pelo @PreAuthorize, sem chamar o caso de uso)")
        void customerCannotDispatchOrDeliver() throws Exception {
            mvc.perform(post("/api/v1/shipments/{id}/dispatch", view.id()).with(TestJwt.customer(customerId)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(post("/api/v1/shipments/{id}/deliver", view.id()).with(TestJwt.customer(customerId)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(changeShipmentStatus);
        }

        @Test
        @DisplayName("operador despacha: comando com a transição certa")
        void operatorDispatches() throws Exception {
            UUID operatorId = UUID.randomUUID();
            given(changeShipmentStatus.change(any())).willReturn(view);

            mvc.perform(post("/api/v1/shipments/{id}/dispatch", view.id()).with(TestJwt.operator(operatorId)))
                    .andExpect(status().isOk());

            verify(changeShipmentStatus).change(eq(new ChangeShipmentStatusCommand(
                    Requester.operatorUser(operatorId), view.id(), new StatusChange.Dispatch())));
        }

        @Test
        @DisplayName("cancelamento exige motivo; transição inválida vira 409")
        void cancel() throws Exception {
            mvc.perform(post("/api/v1/shipments/{id}/cancel", view.id()).with(TestJwt.customer(customerId))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("reason"));

            given(changeShipmentStatus.change(any())).willThrow(new InvalidShipmentStatusTransitionException(
                    ShipmentId.of(view.id()), ShipmentStatus.DELIVERED, ShipmentStatus.CANCELLED));
            mvc.perform(post("/api/v1/shipments/{id}/cancel", view.id()).with(TestJwt.customer(customerId))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Desisti\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_INVALID_STATUS_TRANSITION"));

            verify(changeShipmentStatus).change(eq(new ChangeShipmentStatusCommand(
                    Requester.customer(customerId), view.id(), new StatusChange.Cancel("Desisti"))));
        }

        @Test
        @DisplayName("conflito de concorrência (bloqueio otimista) vira 409 CONCURRENT_UPDATE")
        void concurrentUpdate() throws Exception {
            given(changeShipmentStatus.change(any())).willThrow(new ConcurrentUpdateException(new RuntimeException()));

            mvc.perform(post("/api/v1/shipments/{id}/deliver", view.id()).with(TestJwt.operator(UUID.randomUUID())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        }
    }

    @Nested
    @DisplayName("comportamento transversal")
    class CrossCutting {

        @Test
        @DisplayName("erro inesperado vira 500 genérico, sem vazar detalhes internos")
        void unexpectedErrorIsHidden() throws Exception {
            given(getShipment.get(any(), any())).willThrow(new IllegalStateException("SELECT * FROM segredo"));

            mvc.perform(get("/api/v1/shipments/{id}", view.id()).with(TestJwt.customer(customerId)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail", not(containsString("segredo"))));
        }

        @Test
        @DisplayName("correlation ID: reaproveita um valor seguro e substitui um malicioso")
        void correlationId() throws Exception {
            given(getShipment.get(any(), any())).willReturn(view);

            mvc.perform(get("/api/v1/shipments/{id}", view.id()).with(TestJwt.customer(customerId))
                            .header("X-Correlation-Id", "req-123"))
                    .andExpect(header().string("X-Correlation-Id", "req-123"));
            mvc.perform(get("/api/v1/shipments/{id}", view.id()).with(TestJwt.customer(customerId))
                            .header("X-Correlation-Id", "x\r\nINFO falso no log"))
                    .andExpect(header().string("X-Correlation-Id", not(containsString("falso"))));
        }

        @Test
        @DisplayName("o corpo de erro traz o mesmo correlation ID do header")
        void errorBodyCarriesCorrelationId() throws Exception {
            mvc.perform(get("/api/v1/shipments/{id}", "x").with(TestJwt.customer(customerId))
                            .header("X-Correlation-Id", "abc-789"))
                    .andExpect(jsonPath("$.correlationId").value("abc-789"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }
}
