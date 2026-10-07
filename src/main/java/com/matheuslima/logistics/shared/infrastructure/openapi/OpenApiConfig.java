package com.matheuslima.logistics.shared.infrastructure.openapi;

import com.matheuslima.logistics.shared.application.Requester;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

/**
 * Documentação OpenAPI 3 (Swagger) gerada a partir do código.
 *
 * <h2>Por que gerar a documentação do código (code-first)?</h2>
 * Documentação escrita à mão desatualiza no primeiro refactor. Gerada a partir dos controllers e
 * DTOs, ela reflete sempre o que a API realmente faz. A alternativa <i>contract-first</i> (escrever
 * o YAML OpenAPI antes e gerar o código) é ótima quando vários times precisam combinar o contrato
 * antes da implementação; aqui, code-first é mais simples.
 *
 * <h2>"Gerado do código" não quer dizer "correto"</h2>
 * O springdoc deduz o contrato das assinaturas e anotações, e erra em dois pontos que importam para
 * quem gera clientes a partir do documento (openapi-generator, por exemplo):
 * <ul>
 *   <li>Um {@code @ApiResponse(responseCode = "404")} sem {@code content} herda o tipo de retorno
 *       do método: o 404 de {@code GET /shipments/{id}} apareceria com o corpo de um
 *       {@code ShipmentResponse}. O {@link #problemDetailResponses()} corrige isso para todos os
 *       erros de uma vez, em vez de repetir {@code content = @Content(...)} em cada anotação.</li>
 *   <li>Quando um método declara qualquer {@code @ApiResponse}, o springdoc deixa de gerar o 200
 *       automático. Por isso os controllers declaram o 2xx explicitamente, e o
 *       {@code OpenApiContractIT} falha se alguma operação ficar sem resposta de sucesso.</li>
 * </ul>
 *
 * <p>Acesse {@code /swagger-ui.html} para testar a API pelo navegador: faça login em
 * {@code POST /api/v1/auth/token}, copie o {@code accessToken}, clique em "Authorize" e cole.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Nome do esquema de segurança referenciado pelos endpoints protegidos. */
    public static final String BEARER_SCHEME = "bearerAuth";

    /** Nome do schema dos erros (RFC 9457) em {@code components.schemas}. */
    public static final String PROBLEM_SCHEMA = "Problem";

    static {
        // Requester é injetado pelo RequesterArgumentResolver a partir do token, e não vem da
        // requisição. Sem isso, o Swagger o mostraria como parâmetro de query.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Requester.class);
    }

    @Bean
    OpenAPI logisticsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Logistics API")
                        .version("v1")
                        .description("""
                                API de envios e rastreamento de encomendas.

                                **Fluxo básico:** cadastre-se (`POST /api/v1/users`), obtenha um token \
                                (`POST /api/v1/auth/token`), crie um envio (`POST /api/v1/shipments`) e \
                                acompanhe o rastreamento (`GET /api/v1/shipments/{id}/tracking`).

                                **Erros** seguem a RFC 9457 (Problem Details), com o campo `code` estável \
                                e o `correlationId` para suporte.""")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token obtido em POST /api/v1/auth/token")))
                // Segurança global: todo endpoint exige token, exceto os marcados com
                // @SecurityRequirements vazio (cadastro e login).
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * Respostas transversais documentadas num lugar só, depois que o springdoc montou o documento:
     * <ul>
     *   <li>todo erro (4xx e 5xx) passa a ter corpo {@code application/problem+json} com o schema
     *       {@value #PROBLEM_SCHEMA}, que é o que a API de fato devolve (ver
     *       {@code GlobalExceptionHandler});</li>
     *   <li>todo endpoint protegido ganha o 401, que pode acontecer em qualquer um deles (token
     *       ausente, adulterado ou expirado) e que nenhum controller precisa repetir.</li>
     * </ul>
     * O customizador roda quando o springdoc monta o documento, que fica em cache; as requisições
     * da API não passam por aqui. Ele também é idempotente ({@code putIfAbsent}, {@code setContent}),
     * então montar o documento de novo não duplica nada.
     */
    @Bean
    OpenApiCustomizer problemDetailResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            openApi.getPaths().values().stream()
                    .map(PathItem::readOperations)
                    .flatMap(List::stream)
                    .forEach(OpenApiConfig::documentErrors);
        };
    }

    private static void documentErrors(Operation operation) {
        if (requiresToken(operation)) {
            operation.getResponses().putIfAbsent("401",
                    new ApiResponse().description("Token ausente, inválido ou expirado"));
        }
        operation.getResponses().forEach((status, response) -> {
            if (status.startsWith("4") || status.startsWith("5")) {
                // Instância nova a cada resposta: o objeto vira parte do documento e não deve
                // ser compartilhado entre operações.
                response.setContent(new Content().addMediaType(APPLICATION_PROBLEM_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA))));
            }
        });
    }

    /**
     * Sem {@code security} na operação, vale a exigência global de token. Os endpoints públicos
     * (cadastro e login) declaram {@code @SecurityRequirements} vazio, o que vira uma lista vazia.
     */
    private static boolean requiresToken(Operation operation) {
        return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
    }

    /**
     * O formato de erro descrito à mão, e não lido da classe {@code ProblemDetail} do Spring: lá,
     * as extensões ({@code code}, {@code timestamp}...) ficam num mapa genérico que o Jackson
     * "achata" no JSON, e o schema gerado mostraria um campo {@code properties} que não existe na
     * resposta.
     */
    private static Schema<?> problemSchema() {
        Schema<?> fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema().example("origin.postalCode"))
                .addProperty("message", new StringSchema().example("deve estar no formato 00000-000"));
        Schema<?> problem = new ObjectSchema()
                .description("Erro no formato RFC 9457 (Problem Details), com as extensões code, timestamp e "
                        + "correlationId. Programas devem decidir pelo code, que é estável; o detail é texto "
                        + "para pessoas e pode mudar.")
                .addProperty("type", new StringSchema().format("uri")
                        .example("https://logistics.example.com/problems/shipment-not-found"))
                .addProperty("title", new StringSchema().example("Not Found"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().example("Envio 3f2a8c1e-... não encontrado."))
                .addProperty("instance", new StringSchema().format("uri").example("/api/v1/shipments/3f2a8c1e-..."))
                .addProperty("code", new StringSchema().description("Código estável do erro (catálogo em docs/04)")
                        .example("SHIPMENT_NOT_FOUND"))
                .addProperty("timestamp", new DateTimeSchema())
                .addProperty("correlationId", new StringSchema()
                        .description("Mesmo valor do header X-Correlation-Id e dos logs da requisição"))
                .addProperty("errors", new ArraySchema().items(fieldError)
                        .description("Presente apenas em VALIDATION_FAILED: um item por campo inválido"));
        return problem.required(List.of("type", "title", "status", "detail", "code", "timestamp"));
    }
}
