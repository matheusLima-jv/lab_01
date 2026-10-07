package com.matheuslima.logistics.shipping.application.port.in;

import com.matheuslima.logistics.shared.application.Requester;

import java.util.Objects;

/**
 * Dados para registrar um envio.
 *
 * <p>Repare no que <b>não</b> está aqui: logradouro, bairro, cidade, UF e o valor do frete. O
 * cliente informa só o que ele sabe (CEP, número, complemento, peso); o resto vem de fontes
 * confiáveis (ViaCEP e tabela de frete). Aceitar o frete do cliente seria permitir que ele
 * escolhesse quanto pagar.
 *
 * @param requester   quem está criando (será o dono do envio)
 * @param origin      endereço de coleta
 * @param destination endereço de entrega
 * @param weightGrams peso em gramas
 */
public record CreateShipmentCommand(Requester requester, AddressInput origin, AddressInput destination,
                                    int weightGrams) {

    public CreateShipmentCommand {
        Objects.requireNonNull(requester, "requester must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
    }

    /**
     * Parte do endereço que só o cliente conhece.
     *
     * @param postalCode CEP (com ou sem hífen)
     * @param number     número do imóvel
     * @param complement complemento opcional
     */
    public record AddressInput(String postalCode, String number, String complement) {
    }
}
