package com.matheuslima.logistics.shared.infrastructure.web;

import com.matheuslima.logistics.shared.application.ApplicationException;
import com.matheuslima.logistics.shared.domain.DomainException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * <b>Adaptador de entrada</b> para erros: traduz exceções em respostas HTTP padronizadas
 * (RFC 9457).
 *
 * <h2>Por que um tratador global?</h2>
 * Controllers ficam limpos: nenhum {@code try/catch} repetido em cada endpoint. A regra
 * "qual exceção vira qual status" fica num único lugar, fácil de revisar e testar. Os casos de uso
 * lançam exceções com <i>categoria</i> ({@link ErrorCategory}); este adaptador converte categoria
 * em status, e por isso o núcleo nunca precisa saber o que é HTTP.
 *
 * <h2>Por que estender {@link ResponseEntityExceptionHandler}?</h2>
 * Ele já trata as exceções do próprio Spring MVC (JSON malformado, parâmetro com tipo errado,
 * método HTTP não suportado, rota inexistente...) gerando {@link ProblemDetail}. Sobrescrevemos
 * só o necessário para padronizar o formato (código, timestamp, correlation ID) e para devolver
 * os erros de validação por campo.
 *
 * <h2>Política de log</h2>
 * <ul>
 *   <li>4xx (erro do cliente): sem log de erro. É o sistema funcionando como esperado, e logar
 *       cada senha errada como ERROR só gera ruído e alarmes falsos.</li>
 *   <li>503 (dependência fora): WARN, porque alguém precisa saber, mas não é bug nosso.</li>
 *   <li>500 (inesperado): ERROR com stack trace. A resposta ao cliente é genérica, e detalhes
 *       internos (classes, SQL, caminhos) nunca vazam, pois ajudariam um atacante.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Sugestão de espera (segundos) enviada no header {@code Retry-After} em respostas 503. */
    static final String RETRY_AFTER_SECONDS = "30";

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    // ------------------------------------------------------------------ falhas de negócio

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException exception) {
        return categorized(exception.category(), exception.code(), exception.getMessage(), exception);
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ProblemDetail> handleApplication(ApplicationException exception) {
        return categorized(exception.category(), exception.code(), exception.getMessage(), exception);
    }

    private ResponseEntity<ProblemDetail> categorized(ErrorCategory category, String code, String detail,
                                                      RuntimeException exception) {
        ProblemDetail body = problems.forCategory(category, code, detail);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(body.getStatus());
        switch (category) {
            // RFC 9110: 503 pode indicar quando tentar de novo; clientes educados respeitam.
            case UNAVAILABLE -> {
                log.warn("Dependency unavailable: {}", detail, exception);
                response.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
            }
            // RFC 9110: toda resposta 401 DEVE trazer o desafio WWW-Authenticate.
            case UNAUTHENTICATED -> response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            default -> log.debug("Business rule rejected the request: {} - {}", code, detail);
        }
        return response.body(body);
    }

    // ------------------------------------------------------------------ segurança

    /**
     * Negado por {@code @PreAuthorize}. Sem este handler, o {@code @ExceptionHandler(Exception)}
     * abaixo capturaria a exceção e responderia 500, uma armadilha clássica de quem combina
     * method security com um tratador genérico.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        ProblemDetail body = problems.create(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Você não tem permissão para executar esta operação.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Falhas de autenticação vindas do filtro de segurança (delegadas por
     * {@code ProblemDetailAuthenticationEntryPoint}), para que 401 tenha o mesmo formato dos
     * demais erros. O header {@code WWW-Authenticate} já foi preenchido pelo entry point.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception) {
        String detail = exception instanceof InvalidBearerTokenException
                ? "Token de acesso inválido ou expirado."
                : "Autenticação necessária: envie um token válido no header Authorization (Bearer).";
        ProblemDetail body = problems.create(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", detail);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // ------------------------------------------------------------------ validação na borda

    /** {@code @Valid @RequestBody} inválido: devolve a lista de campos com problema. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        return validationProblem(errors);
    }

    /** Restrições em parâmetros do método ({@code @RequestParam @Max(100) int size}). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<Map<String, String>> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", String.valueOf(result.getMethodParameter().getParameterName()),
                                "message", String.valueOf(error.getDefaultMessage()))))
                .toList();
        return validationProblem(errors);
    }

    /** Corpo ilegível: JSON malformado, tipo errado num campo, enum desconhecido. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail body = problems.create(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "O corpo da requisição está ilegível ou não é um JSON válido para este endpoint.");
        return ResponseEntity.badRequest().body(body);
    }

    /** Parâmetro de URL com tipo errado (ex.: {@code ?status=VOANDO}, UUID inválido no caminho). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String parameter = exception.getPropertyName() != null ? exception.getPropertyName() : "parâmetro";
        ProblemDetail body = problems.create(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Valor inválido para '%s': %s.".formatted(parameter, exception.getValue()));
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Ponto único por onde passam as respostas de erro do próprio Spring MVC: completamos o
     * {@link ProblemDetail} (código, tipo, timestamp, correlation ID) para manter o formato
     * uniforme em toda a API.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problems.enrich(problem, status != null ? status.name() : "HTTP_" + statusCode.value());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    // ------------------------------------------------------------------ inesperado

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected error while processing request", exception);
        ProblemDetail body = problems.create(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocorreu um erro inesperado. Se persistir, contate o suporte informando o correlationId.");
        return ResponseEntity.internalServerError().body(body);
    }

    private ResponseEntity<Object> validationProblem(List<Map<String, String>> errors) {
        ProblemDetail body = problems.create(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "A requisição contém campos inválidos.");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static Map<String, String> toFieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
    }
}
