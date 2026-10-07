package com.matheuslima.logistics.shared.application;

import java.util.function.Supplier;

/**
 * <b>Porta de saída</b> para delimitar transações sem acoplar a camada de aplicação ao Spring.
 *
 * <h2>Por que não usar {@code @Transactional} nos casos de uso?</h2>
 * {@code @Transactional} funciona, e é o que muitos projetos fazem. Aqui escolhemos uma porta
 * explícita por dois motivos:
 * <ol>
 *   <li><b>Pureza do núcleo:</b> os casos de uso não importam nada do Spring (regra verificada
 *       pelo ArchUnit), então são testados com {@code new} e um fake que executa o bloco
 *       diretamente.</li>
 *   <li><b>Controle fino da fronteira, o motivo mais importante:</b> com {@code @Transactional} no
 *       método inteiro, a chamada HTTP à ViaCEP aconteceria <i>dentro</i> da transação, e o
 *       pool de conexões do banco ficaria preso esperando uma API externa. Se a ViaCEP ficar
 *       lenta, o pool (10 conexões por padrão) se esgota e a aplicação inteira para de
 *       responder, inclusive endpoints que nem usam CEP. Com a porta, o caso de uso chama a
 *       ViaCEP primeiro e abre a transação só para gravar. A fronteira fica visível no código.</li>
 * </ol>
 *
 * <h2>Por que existe uma variante somente leitura?</h2>
 * Em transações {@code readOnly}, o Hibernate desliga o <i>dirty checking</i> (não compara
 * snapshots das entidades no flush) e o driver pode otimizar. Em arquiteturas com réplicas, é
 * também o sinal para rotear a consulta para uma réplica de leitura.
 *
 * <p>Implementação: {@code SpringTransactionRunner} (usa {@code TransactionTemplate}).
 */
public interface TransactionRunner {

    /**
     * Executa o bloco em uma transação de leitura e escrita. Se o bloco lançar exceção não
     * checada, a transação sofre rollback e a exceção é propagada sem alteração.
     *
     * @param work trabalho a executar
     * @param <T>  tipo do retorno
     * @return o valor produzido pelo bloco
     */
    <T> T inTransaction(Supplier<T> work);

    /**
     * Executa o bloco em uma transação somente leitura.
     *
     * @param work consulta a executar
     * @param <T>  tipo do retorno
     * @return o valor produzido pelo bloco
     */
    <T> T inReadOnlyTransaction(Supplier<T> work);
}
