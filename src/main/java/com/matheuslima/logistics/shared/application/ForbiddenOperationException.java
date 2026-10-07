package com.matheuslima.logistics.shared.application;

import com.matheuslima.logistics.shared.domain.ErrorCategory;

/**
 * Quem chama está autenticado, mas não tem permissão para a operação (HTTP 403).
 *
 * <h2>Se o controller já usa {@code @PreAuthorize}, por que checar de novo no caso de uso?</h2>
 * <b>Defesa em profundidade.</b> O {@code @PreAuthorize} protege apenas a entrada HTTP. O caso de
 * uso também pode ser chamado por outros adaptadores (um listener de fila, um job, um futuro
 * endpoint gRPC). A regra "só operadores despacham envios" é regra de negócio, então ela
 * precisa morar no núcleo para valer em qualquer caminho de entrada. A anotação no controller
 * continua útil: rejeita cedo e documenta a exigência no OpenAPI.
 *
 * <p>Atenção: quando o problema é acessar um recurso <i>de outra pessoa</i>, respondemos 404
 * (e não 403) para não revelar que o recurso existe. Veja {@link Requester}.
 */
public class ForbiddenOperationException extends ApplicationException {

    public ForbiddenOperationException(String message) {
        super(ErrorCategory.FORBIDDEN, "OPERATION_NOT_ALLOWED", message);
    }
}
