package com.matheuslima.logistics.shared.application;

import com.matheuslima.logistics.shared.domain.ErrorCategory;

/**
 * Uma dependência externa (API de terceiro) não respondeu a tempo, respondeu com erro de
 * servidor ou está com o circuit breaker aberto (HTTP 503).
 *
 * <h2>Por que traduzir exceções técnicas para esta?</h2>
 * O adaptador HTTP da ViaCEP lida com {@code ResourceAccessException},
 * {@code HttpServerErrorException} e {@code CallNotPermittedException} (Resilience4j). Se essas
 * classes vazassem para o caso de uso, o núcleo dependeria do Spring Web e do Resilience4j.
 * Traduzir na borda mantém o núcleo estável: trocar o cliente HTTP ou a biblioteca de
 * resiliência não muda nenhuma linha da camada de aplicação.
 *
 * <h2>Por que 503 e não 500?</h2>
 * 500 diz "nós erramos". 503 diz "estamos temporariamente sem condições, tente de novo", o que
 * é verdade quando uma dependência caiu. Clientes bem escritos fazem retry com backoff em 503,
 * mas não em 500.
 */
public class ExternalServiceUnavailableException extends ApplicationException {

    private final String serviceName;

    public ExternalServiceUnavailableException(String serviceName, Throwable cause) {
        super(ErrorCategory.UNAVAILABLE, "EXTERNAL_SERVICE_UNAVAILABLE",
                "O serviço externo '%s' está indisponível no momento. Tente novamente em instantes."
                        .formatted(serviceName),
                cause);
        this.serviceName = serviceName;
    }

    public String serviceName() {
        return serviceName;
    }
}
