package com.matheuslima.logistics.shipping.application;

import com.matheuslima.logistics.shared.application.ApplicationException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;
import com.matheuslima.logistics.shipping.domain.PostalCode;

/**
 * O CEP tem formato válido, mas não existe na base dos Correios (HTTP 422).
 *
 * <h2>Por que 422 e não 400 ou 404?</h2>
 * <ul>
 *   <li>Não é 400: a requisição está sintaticamente correta (o CEP tem 8 dígitos).</li>
 *   <li>Não é 404: o recurso da URL ({@code /shipments}) existe; o que não existe é um dado
 *       <i>referenciado</i> no corpo.</li>
 *   <li>É 422 (Unprocessable Content, RFC 9110): o servidor entendeu o pedido, mas o conteúdo
 *       não permite processá-lo.</li>
 * </ul>
 */
public class PostalCodeNotFoundException extends ApplicationException {

    /**
     * @param postalCode CEP não encontrado
     * @param role       papel do endereço na requisição ("origem" ou "destino"), para o cliente
     *                   saber qual campo corrigir
     */
    public PostalCodeNotFoundException(PostalCode postalCode, String role) {
        super(ErrorCategory.UNPROCESSABLE, "POSTAL_CODE_NOT_FOUND",
                "CEP de %s não encontrado: %s.".formatted(role, postalCode.formatted()));
    }
}
