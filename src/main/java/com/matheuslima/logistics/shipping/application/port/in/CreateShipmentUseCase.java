package com.matheuslima.logistics.shipping.application.port.in;

/**
 * <b>Porta de entrada</b>: registrar um novo envio.
 *
 * <p>Fluxo: valida o peso, consulta os dois CEPs na fonte externa (ViaCEP), calcula o frete,
 * grava o envio e agenda a publicação do evento {@code shipment.created}, tudo em uma
 * transação curta. Detalhes e motivos em {@code CreateShipmentService}.
 */
public interface CreateShipmentUseCase {

    /**
     * @throws com.matheuslima.logistics.shared.domain.DomainValidationException dados inválidos (400)
     * @throws com.matheuslima.logistics.shipping.application.PostalCodeNotFoundException CEP
     *         inexistente (422)
     * @throws com.matheuslima.logistics.shared.application.ExternalServiceUnavailableException
     *         ViaCEP indisponível (503)
     */
    ShipmentView create(CreateShipmentCommand command);
}
