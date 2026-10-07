package com.matheuslima.logistics.shared.application;

import com.matheuslima.logistics.shared.domain.ErrorCategory;

/**
 * Duas operações tentaram alterar o mesmo agregado ao mesmo tempo e esta perdeu a corrida
 * (HTTP 409).
 *
 * <h2>Como o conflito é detectado: bloqueio otimista</h2>
 * Cada linha de {@code shipments} tem uma coluna {@code version}. O UPDATE gerado pelo Hibernate
 * é {@code ... WHERE id = ? AND version = ?}. Se outra transação já incrementou a versão, nenhuma
 * linha é afetada e o Hibernate acusa conflito no commit. Ninguém fica bloqueado esperando
 * (diferente do bloqueio pessimista, {@code SELECT ... FOR UPDATE}), o que escala melhor quando
 * conflitos são raros, como aqui.
 *
 * <h2>Por que uma exceção do núcleo, se quem detecta é o Hibernate?</h2>
 * O conflito aparece no commit, dentro do {@code SpringTransactionRunner}, que traduz a exceção do
 * Spring ({@code OptimisticLockingFailureException}) para esta. Assim o núcleo e os adaptadores
 * de entrada continuam sem conhecer Spring Data, e o conflito ganha categoria e código como
 * qualquer outra falha esperada.
 */
public class ConcurrentUpdateException extends ApplicationException {

    public ConcurrentUpdateException(Throwable cause) {
        super(ErrorCategory.CONFLICT, "CONCURRENT_UPDATE",
                "O recurso foi alterado por outra operação ao mesmo tempo. Consulte o estado atual e tente novamente.",
                cause);
    }
}
