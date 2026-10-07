package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shared.application.PageResult;

/**
 * <b>Porta de entrada</b>: listar envios com paginação e filtro opcional por status.
 *
 * <p>O escopo da listagem depende de quem pede: operadores veem todos os envios, clientes veem
 * apenas os seus. A regra fica no caso de uso, e não no controller, para valer em qualquer
 * adaptador de entrada.
 */
public interface ListShipmentsUseCase {

    PageResult<ShipmentView> list(ListShipmentsQuery query);
}
