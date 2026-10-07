package com.matheuslima.logistics.shared.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repositório Spring Data da tabela de outbox.
 *
 * <p>Diferente das portas do núcleo, este repositório é infraestrutura pura: só é usado por
 * classes de infraestrutura (relay e publicadores de outbox dos contextos). Por isso pode
 * expor tipos do Spring Data sem problema.
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /**
     * Seleciona e <b>trava</b> o próximo lote de mensagens pendentes, pulando as que já estão
     * travadas por outra transação.
     *
     * <h2>Por que {@code FOR UPDATE SKIP LOCKED}?</h2>
     * Com várias instâncias da aplicação rodando (escala horizontal), todas executam o relay. Sem
     * coordenação, duas instâncias pegariam a mesma mensagem e a publicariam duas vezes.
     * <ul>
     *   <li>{@code FOR UPDATE} trava as linhas lidas até o fim da transação.</li>
     *   <li>{@code SKIP LOCKED} faz as outras instâncias <i>pularem</i> as linhas travadas, em vez
     *       de esperarem, e pegarem o lote seguinte.</li>
     * </ul>
     * Resultado: várias instâncias dividem o trabalho em paralelo, sem bloqueio mútuo e sem
     * precisar de um coordenador externo (ShedLock, eleição de líder). É um recurso do PostgreSQL
     * 9.5+ (também existe no MySQL 8 e no Oracle), por isso a consulta é nativa: o JPQL não
     * expressa {@code SKIP LOCKED} de forma direta.
     *
     * @param batchSize tamanho máximo do lote
     * @return mensagens pendentes mais antigas primeiro (ordem de criação)
     */
    @Query(value = """
            SELECT *
              FROM outbox_messages
             WHERE published_at IS NULL
             ORDER BY created_at
             LIMIT :batchSize
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessage> lockNextPendingBatch(@Param("batchSize") int batchSize);

    /** Quantidade de mensagens ainda não publicadas (útil para monitoramento e testes). */
    long countByPublishedAtIsNull();
}
