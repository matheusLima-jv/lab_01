package com.matheuslima.logistics.shared.application.fake;

import com.matheuslima.logistics.shared.application.TransactionRunner;

import java.util.function.Supplier;

/**
 * Implementação de teste do {@link TransactionRunner}: executa o bloco diretamente, sem banco.
 *
 * <h2>Fake versus mock: por que fakes aqui?</h2>
 * Um <b>mock</b> (Mockito) verifica <i>interações</i> ("o método X foi chamado com Y"). Um
 * <b>fake</b> é uma implementação de verdade, só que simplificada (em memória). Testes com fakes
 * verificam <i>estado e comportamento</i> e quebram menos quando refatoramos o "como" sem mudar
 * o "o quê". Essa é a escola clássica de TDD (Detroit/Chicago), em oposição à escola mockista
 * (Londres). Os dois estilos aparecem neste projeto: fakes nos casos de uso, mocks nos testes de
 * controller, onde a interação com o caso de uso é justamente o que se quer verificar.
 *
 * <p>Este fake ainda expõe {@link #isActive()}. Com ele, os outros fakes registram se foram
 * chamados dentro ou fora de uma transação, e os testes conseguem verificar decisões de
 * arquitetura como "a ViaCEP é chamada FORA da transação".
 */
public class DirectTransactionRunner implements TransactionRunner {

    private boolean active;
    private boolean readOnly;
    private int committed;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return run(work, false);
    }

    @Override
    public <T> T inReadOnlyTransaction(Supplier<T> work) {
        return run(work, true);
    }

    private <T> T run(Supplier<T> work, boolean readOnlyTx) {
        if (active) {
            throw new IllegalStateException("nested transactions are not expected in these use cases");
        }
        active = true;
        readOnly = readOnlyTx;
        try {
            T result = work.get();
            committed++;
            return result;
        } finally {
            active = false;
            readOnly = false;
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** Quantas transações terminaram sem exceção (equivalente a "commits"). */
    public int committedCount() {
        return committed;
    }
}
