package com.matheuslima.logistics.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato mínimo de um <b>evento de domínio</b>: um fato de negócio que JÁ aconteceu.
 * Por isso os nomes ficam sempre no passado ({@code Created}, {@code Dispatched},
 * {@code Cancelled}): um evento não é um pedido ("despache o envio"), é um registro
 * ("o envio foi despachado").
 *
 * <h2>Por que modelar eventos de domínio?</h2>
 * <ul>
 *   <li><b>Desacoplamento:</b> o agregado apenas registra o que aconteceu. Quem reage ao
 *       fato (histórico de rastreamento, notificação, faturamento...) fica fora do domínio.
 *       Adicionar um novo interessado não exige alterar o agregado (princípio aberto/fechado).</li>
 *   <li><b>Linguagem ubíqua:</b> eventos têm nomes que o negócio reconhece, o que aproxima o
 *       código da conversa com os especialistas do domínio.</li>
 *   <li><b>Integração entre sistemas:</b> são a matéria-prima das mensagens publicadas no
 *       RabbitMQ (ver {@code OutboxShipmentEventPublisher}).</li>
 * </ul>
 *
 * <h2>Por que todo evento tem um {@code eventId}?</h2>
 * A entrega de mensagens adotada aqui é "pelo menos uma vez" (<i>at-least-once</i>): em caso
 * de falha, a mesma mensagem pode chegar duas vezes. Um identificador único por evento permite
 * que o consumidor detecte a duplicata e a ignore, ou seja, seja <b>idempotente</b>.
 *
 * <h2>Por que interface e não classe abstrata?</h2>
 * Os eventos concretos são {@code record}s (imutáveis por construção, com {@code equals}/
 * {@code hashCode} corretos de graça), e records não podem estender classes. Uma interface
 * define o contrato sem impor herança.
 */
public interface DomainEvent {

    /**
     * Identificador único do evento. Serve como chave de idempotência para os consumidores.
     *
     * @return identificador do evento
     */
    UUID eventId();

    /**
     * Instante em que o fato ocorreu no domínio. Não confundir com o instante em que a
     * mensagem foi publicada no broker, que pode ser alguns segundos depois (ver outbox).
     *
     * @return instante da ocorrência, sempre em UTC ({@link Instant} não tem fuso)
     */
    Instant occurredAt();
}
