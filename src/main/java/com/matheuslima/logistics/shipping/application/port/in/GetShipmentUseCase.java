package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shared.application.Requester;

import java.util.UUID;

/**
 * <b>Porta de entrada</b>: consultar um envio pelo ID.
 *
 * <p>Clientes veem apenas os próprios envios; para os demais, a resposta é 404, como se o envio
 * não existisse (ver {@code ShipmentAccess}).
 */
public interface GetShipmentUseCase {

    ShipmentView get(Requester requester, UUID shipmentId);
}
