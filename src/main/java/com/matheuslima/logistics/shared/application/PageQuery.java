package com.matheuslima.logistics.shared.application;

import com.matheuslima.logistics.shared.domain.DomainValidationException;

/**
 * Pedido de paginação independente de framework.
 *
 * <h2>Por que não usar {@code Pageable} do Spring Data?</h2>
 * {@code Pageable} é um tipo de infraestrutura de persistência. Se ele aparecesse na assinatura
 * de um caso de uso, a camada de aplicação passaria a depender do Spring Data. O adaptador web
 * cria um {@code PageQuery}; o adaptador JPA o converte em {@code PageRequest}. Cada camada fala
 * a sua língua e a tradução fica nas bordas.
 *
 * <h2>Por que limitar o tamanho da página?</h2>
 * Sem limite, um cliente pode pedir {@code size=1000000} e derrubar a aplicação por falta de
 * memória ou travar o banco. É uma proteção simples contra abuso, acidental ou intencional.
 *
 * @param page número da página, começando em 0 (convenção do Spring Data)
 * @param size quantidade de itens por página, entre 1 e {@link #MAX_SIZE}
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new DomainValidationException("INVALID_PAGE", "A página deve ser maior ou igual a 0.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new DomainValidationException("INVALID_PAGE_SIZE",
                    "O tamanho da página deve estar entre 1 e %d.".formatted(MAX_SIZE));
        }
    }

    public static PageQuery firstPage() {
        return new PageQuery(0, DEFAULT_SIZE);
    }

    /** Deslocamento absoluto do primeiro item da página (útil para SQL com OFFSET). */
    public long offset() {
        return (long) page * size;
    }
}
