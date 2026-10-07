package com.matheuslima.logistics.shared.infrastructure.web;

import com.matheuslima.logistics.shared.domain.ErrorCategory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.util.Locale;

/**
 * Monta respostas de erro no formato <b>Problem Details</b> (RFC 9457, sucessora da RFC 7807).
 *
 * <h2>Por que um formato padronizado de erro?</h2>
 * Sem padrão, cada endpoint inventa o seu ({@code {"error": ...}}, {@code {"message": ...}},
 * {@code {"msg": ...}}), e cada cliente precisa de um tratamento por endpoint. Com a RFC 9457,
 * todo erro da API tem o mesmo formato, com {@code Content-Type: application/problem+json}:
 * <pre>
 * {
 *   "type": "https://logistics.example.com/problems/shipment-not-found",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "Envio 3f2a... não encontrado.",
 *   "instance": "/api/v1/shipments/3f2a...",
 *   "code": "SHIPMENT_NOT_FOUND",
 *   "timestamp": "2026-01-15T10:00:00Z",
 *   "correlationId": "b7c1..."
 * }
 * </pre>
 * <ul>
 *   <li>{@code type}: identificador do tipo de problema. Usa o domínio reservado
 *       {@code example.com} (RFC 2606) como marcador; em um projeto real, aponte para a página de
 *       documentação de erros da empresa.</li>
 *   <li>{@code code}: extensão nossa, estável, para clientes tomarem decisão sem interpretar texto.</li>
 *   <li>{@code correlationId}: o mesmo ID dos logs. Quando um cliente reportar um erro, esse ID
 *       leva direto às linhas de log da requisição.</li>
 * </ul>
 *
 * <p>É um bean (e não métodos estáticos) para receber o {@link Clock} injetado, o que torna o
 * {@code timestamp} testável.
 */
@Component
public class ProblemDetailFactory {

    static final String TYPE_BASE_URI = "https://logistics.example.com/problems/";

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    /** Problema a partir de uma falha de negócio categorizada (domínio ou aplicação). */
    public ProblemDetail forCategory(ErrorCategory category, String code, String detail) {
        return create(statusOf(category), code, detail);
    }

    /** Problema com status explícito (erros técnicos: validação, segurança, erro interno). */
    public ProblemDetail create(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(problem, code);
        return problem;
    }

    /**
     * Completa um {@link ProblemDetail} criado pelo próprio Spring MVC (JSON malformado, método
     * não suportado...), para que <b>todo</b> erro da API tenha o mesmo formato.
     */
    public void enrich(ProblemDetail problem, String code) {
        problem.setType(URI.create(TYPE_BASE_URI + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        HttpStatus status = HttpStatus.resolve(problem.getStatus());
        if (status != null) {
            problem.setTitle(status.getReasonPhrase());
        }
        problem.setProperty("code", code);
        problem.setProperty("timestamp", clock.instant());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }

    /**
     * A tradução categoria → status HTTP fica concentrada AQUI, num {@code switch} exaustivo:
     * uma nova categoria sem status definido não compila.
     */
    public static HttpStatus statusOf(ErrorCategory category) {
        return switch (category) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
