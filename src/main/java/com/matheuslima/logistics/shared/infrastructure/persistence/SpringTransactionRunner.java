package com.matheuslima.logistics.shared.infrastructure.persistence;

import com.matheuslima.logistics.shared.application.ConcurrentUpdateException;
import com.matheuslima.logistics.shared.application.TransactionRunner;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * <b>Adaptador</b> da porta {@link TransactionRunner} usando o gerenciamento de transações do
 * Spring.
 *
 * <h2>{@code TransactionTemplate}: a forma programática de {@code @Transactional}</h2>
 * {@code @Transactional} funciona por proxy (AOP) e tem armadilhas conhecidas: chamar um método
 * anotado de dentro da mesma classe ignora a transação (<i>self-invocation</i>), e a fronteira
 * fica implícita. O {@code TransactionTemplate} faz o mesmo trabalho (begin, commit, rollback em
 * {@code RuntimeException}) de forma explícita, sem proxy: o que está dentro do lambda está na
 * transação, e o que está fora não está.
 *
 * <h2>Duas instâncias de template</h2>
 * {@code TransactionTemplate} guarda a configuração (somente leitura, isolamento, timeout) no
 * próprio objeto. Por isso há uma instância para leitura/escrita e outra somente leitura, ambas
 * thread-safe e reutilizáveis por todas as requisições.
 *
 * <h2>Tradução de exceções</h2>
 * O conflito de bloqueio otimista só aparece no commit, quando o Hibernate executa o
 * {@code UPDATE ... WHERE version = ?}. É aqui, portanto, que a exceção do Spring vira a
 * exceção do núcleo ({@link ConcurrentUpdateException}).
 *
 * <p>Qual {@link PlatformTransactionManager} é injetado? O {@code JpaTransactionManager}
 * auto-configurado pelo Spring Boot. O MongoDB e o RabbitMQ não participam dessas transações; veja
 * os casos de uso para entender por que isso é uma decisão consciente.
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate readWrite;
    private final TransactionTemplate readOnly;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.readWrite = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        try {
            return readWrite.execute(status -> work.get());
        } catch (OptimisticLockingFailureException conflict) {
            throw new ConcurrentUpdateException(conflict);
        }
    }

    @Override
    public <T> T inReadOnlyTransaction(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }
}
