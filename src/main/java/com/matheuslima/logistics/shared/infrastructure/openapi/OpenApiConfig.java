package com.matheuslima.logistics.shared.infrastructure.openapi;

import com.matheuslima.logistics.shared.application.Requester;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação OpenAPI 3 (Swagger) gerada a partir do código.
 *
 * <h2>Por que gerar a documentação do código (code-first)?</h2>
 * Documentação escrita à mão desatualiza no primeiro refactor. Gerada a partir dos controllers e
 * DTOs, ela reflete sempre o que a API realmente faz. A alternativa <i>contract-first</i> (escrever
 * o YAML OpenAPI antes e gerar o código) é ótima quando vários times precisam combinar o contrato
 * antes da implementação; aqui, code-first é mais simples.
 *
 * <p>Acesse {@code /swagger-ui.html} para testar a API pelo navegador: faça login em
 * {@code POST /api/v1/auth/token}, copie o {@code accessToken}, clique em "Authorize" e cole.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Nome do esquema de segurança referenciado pelos endpoints protegidos. */
    public static final String BEARER_SCHEME = "bearerAuth";

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
}
