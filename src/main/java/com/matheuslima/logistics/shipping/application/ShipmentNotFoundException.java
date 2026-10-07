package com.matheuslima.logistics.shipping.application;

import com.matheuslima.logistics.shared.application.ApplicationException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;
import com.matheuslima.logistics.shipping.domain.ShipmentId;

/**
 * Envio inexistente <b>ou</b> pertencente a outro cliente (HTTP 404).
 *
 * <h2>Por que 404 também para "existe, mas não é seu"?</h2>
 * Responder 403 confirmaria que o ID existe. Com IDs previsíveis isso permitiria mapear quantos
 * envios a empresa tem; mesmo com UUIDs, é informação que não precisamos dar. A OWASP chama a
 * falha oposta de IDOR (Insecure Direct Object Reference), uma das mais comuns em APIs (OWASP
 * API Security Top 10, item 1: Broken Object Level Authorization). Para o cliente legítimo não
 * faz diferença: o envio não está disponível para ele.
 */
public class ShipmentNotFoundException extends ApplicationException {

    public ShipmentNotFoundException(ShipmentId id) {
        super(ErrorCategory.NOT_FOUND, "SHIPMENT_NOT_FOUND", "Envio %s não encontrado.".formatted(id));
    }
}
