package com.matheuslima.logistics.shared.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Classe base para <b>raízes de agregado</b> que emitem eventos de domínio.
 *
 * <h2>O que é um agregado (DDD)?</h2>
 * Um agregado é um conjunto de objetos tratado como uma unidade de consistência. A <b>raiz</b>
 * é a única porta de entrada: código externo nunca altera o estado interno diretamente, apenas
 * chama métodos de negócio da raiz, e é ela que garante as <i>invariantes</i> (regras que nunca
 * podem ficar violadas, ex.: "um envio entregue não pode ser cancelado").
 * Regra prática derivada disso: <b>uma transação altera um único agregado</b>.
 *
 * <h2>Por que guardar os eventos aqui em vez de publicá-los imediatamente?</h2>
 * Se o agregado publicasse direto no RabbitMQ:
 * <ol>
 *   <li>o domínio passaria a depender de infraestrutura (violando a regra de dependência da
 *       arquitetura hexagonal);</li>
 *   <li>o evento poderia sair <i>antes</i> do commit no banco, e se o commit falhasse o
 *       mundo externo seria avisado de algo que nunca aconteceu.</li>
 * </ol>
 * Em vez disso, o agregado apenas <i>registra</i> os eventos. O caso de uso os retira com
 * {@link #pullDomainEvents()} e os entrega à porta de publicação <b>dentro da mesma
 * transação</b> que salva o agregado (padrão Transactional Outbox). Estado e eventos são
 * gravados juntos, ou nenhum dos dois é gravado.
 *
 * <h2>Por que "pull" (retirar) e não "get"?</h2>
 * Retirar e limpar a lista impede que o mesmo evento seja publicado duas vezes caso o
 * agregado seja salvo de novo na mesma operação.
 *
 * <p>A ideia é a mesma do {@code AbstractAggregateRoot} do Spring Data, mas implementada sem
 * Spring: o domínio continua Java puro, regra verificada automaticamente pelos testes do
 * ArchUnit ({@code ArchitectureTest}).
 *
 * <p><b>Observação:</b> nem todo agregado precisa emitir eventos. O {@code User} do contexto
 * de identidade, por exemplo, não estende esta classe porque nenhum consumidor precisa saber
 * que um usuário foi criado. Herança só quando há comportamento a herdar.
 *
 * @param <E> tipo dos eventos emitidos. A tipagem forte impede, em tempo de compilação, que um
 *            agregado registre eventos que pertencem a outro.
 */
public abstract class AggregateRoot<E extends DomainEvent> {

    /**
     * Eventos ocorridos e ainda não publicados. Não é {@code final} na semântica de conteúdo:
     * a lista muda, mas a referência não. Não é thread-safe de propósito: agregados não são
     * compartilhados entre threads (cada requisição carrega sua própria instância).
     */
    private final List<E> domainEvents = new ArrayList<>();

    /**
     * Registra um fato ocorrido. É {@code protected} porque só os métodos de negócio da própria
     * raiz sabem quando um fato aconteceu; código externo não pode "inventar" eventos.
     *
     * @param event evento a registrar
     */
    protected void registerEvent(E event) {
        domainEvents.add(Objects.requireNonNull(event, "event must not be null"));
    }

    /**
     * Retorna os eventos pendentes e limpa a lista interna.
     *
     * @return cópia imutável dos eventos, na ordem em que ocorreram
     */
    public List<E> pullDomainEvents() {
        List<E> pending = List.copyOf(domainEvents);
        domainEvents.clear();
        return pending;
    }
}
