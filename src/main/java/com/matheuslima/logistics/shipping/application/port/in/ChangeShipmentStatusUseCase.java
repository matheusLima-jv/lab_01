package com.matheuslima.logistics.shipping.application.port.in;

/**
 * <b>Porta de entrada</b>: mover um envio pelo ciclo de vida (despachar, entregar, cancelar).
 *
 * <h2>Por que um único caso de uso para três ações?</h2>
 * As três seguem o mesmo roteiro: carregar o envio visível para quem pede, verificar permissão,
 * aplicar a transição no agregado, salvar e publicar o evento. O que varia é só a transição, e
 * isso é modelado como dado ({@link StatusChange}). Três serviços quase idênticos seriam
 * duplicação; um serviço com {@code switch} exaustivo sobre um tipo selado é coeso e seguro.
 */
public interface ChangeShipmentStatusUseCase {

    /**
     * @throws com.matheuslima.logistics.shipping.application.ShipmentNotFoundException envio
     *         inexistente ou de outro cliente (404)
     * @throws com.matheuslima.logistics.shared.application.ForbiddenOperationException cliente
     *         tentando despachar/entregar (403)
     * @throws com.matheuslima.logistics.shipping.domain.InvalidShipmentStatusTransitionException
     *         transição não permitida a partir do estado atual (409)
     */
    ShipmentView change(ChangeShipmentStatusCommand command);
}
