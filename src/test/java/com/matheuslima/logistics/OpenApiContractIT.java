package com.matheuslima.logistics;

import com.matheuslima.logistics.shared.infrastructure.openapi.OpenApiConfig;
import com.matheuslima.logistics.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o documento OpenAPI publicado em {@code /v3/api-docs}, e não as anotações dos controllers.
 *
 * <h2>Por que o documento é parte do contrato</h2>
 * Quem integra com a API lê o Swagger UI, e muitos times geram o cliente direto do documento
 * (openapi-generator). Um erro ali vira código errado do outro lado: sem a resposta 200, o cliente
 * gerado não sabe o tipo de retorno; com o schema errado num 404, ele tenta ler um
 * {@code ShipmentResponse} onde vem um Problem Details.
 *
 * <h2>Por que testar o documento gerado</h2>
 * O documento é montado pelo springdoc a partir de anotações, assinaturas, configuração e do
 * customizador em {@link OpenApiConfig}. Só o resultado final mostra o que o cliente recebe. As
 * regras abaixo são gerais (valem para toda operação), então um endpoint novo que esquecer o 2xx
 * quebra o build sem que ninguém precise lembrar de escrever um teste para ele.
 */
@DisplayName("Contrato OpenAPI publicado")
class OpenApiContractIT extends AbstractIntegrationTest {

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

    @Test
    @DisplayName("toda operação documenta uma resposta de sucesso (2xx) em application/json")
    void everyOperationDocumentsSuccess() {
        operations().forEach((name, operation) -> {
            Map<String, JsonNode> success = responses(operation, "2");
            assertThat(success).as("%s sem resposta 2xx", name).isNotEmpty();
            success.values().stream()
                    .filter(response -> response.has("content"))
                    .forEach(response -> assertThat(response.path("content").propertyNames())
                            .as("%s: tipo da resposta de sucesso", name)
                            .containsExactly("application/json"));
        });
    }

    @Test
    @DisplayName("todo erro documentado é application/problem+json com o schema Problem")
    void everyErrorIsProblemDetails() {
        operations().forEach((name, operation) -> {
            Map<String, JsonNode> errors = responses(operation, "4");
            errors.putAll(responses(operation, "5"));
            errors.forEach((status, response) -> {
                JsonNode content = response.path("content");
                assertThat(content.propertyNames()).as("%s %s: tipo", name, status)
                        .containsExactly("application/problem+json");
                assertThat(content.path("application/problem+json").path("schema").path("$ref").asString())
                        .as("%s %s: schema", name, status)
                        .isEqualTo("#/components/schemas/" + OpenApiConfig.PROBLEM_SCHEMA);
            });
        });
    }

    @Test
    @DisplayName("só cadastro e login são públicos; todo o resto documenta o 401")
    void securedOperationsDocumentUnauthorized() {
        Map<String, JsonNode> operations = operations();

        Set<String> publicOperations = operations.entrySet().stream()
                .filter(entry -> isPublic(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        // Deve bater com os permitAll do SecurityConfig: se divergir, o Swagger promete algo que a
        // API não cumpre (ou esconde o cadeado de um endpoint protegido).
        assertThat(publicOperations).containsExactly("POST /api/v1/auth/token", "POST /api/v1/users");

        operations.forEach((name, operation) -> {
            if (!publicOperations.contains(name)) {
                assertThat(responses(operation, "4")).as("%s sem 401", name).containsKey("401");
            }
        });
    }

    /** Operações do documento indexadas por "MÉTODO /caminho", ex.: "POST /api/v1/shipments". */
    private Map<String, JsonNode> operations() {
        JsonNode document = http.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        Map<String, JsonNode> operations = new TreeMap<>();
        document.path("paths").properties().forEach(path -> path.getValue().properties().stream()
                .filter(entry -> HTTP_METHODS.contains(entry.getKey()))
                .forEach(entry -> operations.put(
                        entry.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey(), entry.getValue())));
        assertThat(operations).as("operações no documento").isNotEmpty();
        return operations;
    }

    /** {@code "security": []} na operação anula a exigência global de token: o endpoint é público. */
    private static boolean isPublic(JsonNode operation) {
        JsonNode security = operation.path("security");
        return security.isArray() && security.isEmpty();
    }

    /** Respostas da operação cujo status começa com o prefixo dado ("2" = sucesso, "4"...). */
    private static Map<String, JsonNode> responses(JsonNode operation, String statusPrefix) {
        Map<String, JsonNode> responses = new TreeMap<>();
        operation.path("responses").properties().stream()
                .filter(entry -> entry.getKey().startsWith(statusPrefix))
                .forEach(entry -> responses.put(entry.getKey(), entry.getValue()));
        return responses;
    }
}
