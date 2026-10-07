package com.matheuslima.logistics.shared.infrastructure.web;

import com.matheuslima.logistics.shared.application.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * Formato JSON das respostas paginadas da API.
 *
 * <pre>
 * { "content": [...], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
 * </pre>
 *
 * <p>Contrato estável e controlado por nós (ver {@link PageResult} sobre por que não serializar
 * o {@code Page} do Spring Data). Nomes familiares para quem já usou Spring, sem expor nada dele.
 *
 * @param content       itens da página
 * @param page          número da página (base 0)
 * @param size          tamanho de página solicitado
 * @param totalElements total de itens
 * @param totalPages    total de páginas
 * @param <T>           tipo dos itens
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<? super S, ? extends T> mapper) {
        PageResult<T> mapped = result.map(mapper);
        return new PageResponse<>(mapped.items(), mapped.page(), mapped.size(), mapped.totalItems(),
                mapped.totalPages());
    }
}
