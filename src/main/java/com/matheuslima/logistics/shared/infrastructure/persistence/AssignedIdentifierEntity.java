package com.matheuslima.logistics.shared.infrastructure.persistence;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Base para entidades JPA cujo ID é atribuído pela aplicação (UUID gerado no domínio) e que
 * <b>não</b> têm coluna {@code @Version}.
 *
 * <h2>O problema: um SELECT "fantasma" antes de cada INSERT</h2>
 * O {@code save()} do Spring Data decide entre {@code persist} (INSERT) e {@code merge} (UPDATE)
 * perguntando "a entidade é nova?". A regra padrão é: "é nova se o ID for nulo". Com IDs gerados
 * pela aplicação, o ID nunca é nulo, então o Spring Data chama {@code merge}, e o Hibernate faz
 * um SELECT para descobrir se a linha existe antes de inserir. Funciona, mas dobra o número de
 * idas ao banco em toda inserção.
 *
 * <h2>A solução: implementar {@link Persistable}</h2>
 * A entidade passa a responder {@code isNew()} por conta própria: nasce "nova" e deixa de ser
 * quando é carregada ({@code @PostLoad}) ou gravada ({@code @PostPersist}). O campo é
 * {@code @Transient}, então não vai para o banco.
 *
 * <p>Entidades com {@code @Version} (como a de envios) não precisam disso: para elas, o Spring
 * Data considera "nova" a entidade com versão nula.
 */
@MappedSuperclass
public abstract class AssignedIdentifierEntity implements Persistable<UUID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
