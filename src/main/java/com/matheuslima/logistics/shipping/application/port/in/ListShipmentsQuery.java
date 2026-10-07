package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shared.application.PageQuery;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shipping.domain.ShipmentStatus;

import java.util.Objects;

/**
 * Consulta paginada de envios (padrão <b>Query</b>, a contraparte de leitura do Command).
 *
 * <p>Separar Commands (mudam estado) de Queries (só leem) é a ideia central do CQS
 * (Command-Query Separation, Bertrand Meyer): métodos que leem não têm efeitos colaterais, o
 * que os torna seguros para cache, retry e réplicas de leitura.
 *
 * @param requester quem consulta (define o escopo: todos ou só os próprios)
 * @param status    filtro opcional; {@code null} significa "qualquer status"
 * @param page      paginação
 */
public record ListShipmentsQuery(Requester requester, ShipmentStatus status, PageQuery page) {

    public ListShipmentsQuery {
        Objects.requireNonNull(requester, "requester must not be null");
        Objects.requireNonNull(page, "page must not be null");
    }
}
