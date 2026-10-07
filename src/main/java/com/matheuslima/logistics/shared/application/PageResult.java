package com.matheuslima.logistics.shared.application;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Página de resultados independente de framework (contraparte de {@link PageQuery}).
 *
 * <p>Pelo mesmo motivo de {@code PageQuery}, não usamos {@code Page} do Spring Data: o núcleo
 * não deve conhecer tipos de persistência. Há um segundo motivo, prático: serializar
 * {@code PageImpl} diretamente em JSON gera um formato instável, que muda entre versões do
 * Spring Data (o próprio Spring emite um aviso sobre isso). Com um tipo nosso, o formato da
 * resposta é um contrato que controlamos.
 *
 * @param items      itens da página atual (cópia imutável)
 * @param page       número da página (base 0)
 * @param size       tamanho de página solicitado
 * @param totalItems total de itens em todas as páginas
 * @param <T>        tipo dos itens
 */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public PageResult {
        // List.copyOf faz cópia defensiva e garante imutabilidade: quem recebe a página não
        // consegue alterá-la, e quem a criou não consegue alterá-la depois.
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }

    /** Total de páginas, calculado sem ponto flutuante (divisão inteira arredondando para cima). */
    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalItems + size - 1) / size);
    }

    /**
     * Converte os itens mantendo os metadados de paginação. É assim que uma página de agregados
     * vira uma página de views (aplicação) e depois uma página de DTOs (web).
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = items.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalItems);
    }
}
