package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shared.application.Requester;

import java.util.List;
import java.util.UUID;

/**
 * <b>Porta de entrada</b>: histórico de rastreamento de um envio (linha do tempo).
 *
 * <h2>Consistência eventual: o que o cliente deve esperar</h2>
 * O histórico é um <i>read model</i> no MongoDB, alimentado por eventos que passam pelo outbox e
 * pelo RabbitMQ. Logo após criar ou alterar um envio, o histórico pode levar alguns instantes
 * (tipicamente menos de 1 segundo) para refletir a mudança. É o preço de desacoplar a gravação
 * da projeção de leitura, e precisa estar documentado no contrato da API.
 */
public interface GetTrackingHistoryUseCase {

    /**
     * @return entradas em ordem cronológica (da mais antiga para a mais recente)
     * @throws com.matheuslima.logistics.shipping.application.ShipmentNotFoundException se o envio
     *         não existir ou não for visível para quem pede
     */
    List<TrackingEntryView> history(Requester requester, UUID shipmentId);
}
