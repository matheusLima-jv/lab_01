package com.matheuslima.logistics.shipping.adapter.in.web;

import com.matheuslima.logistics.shipping.domain.Weight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/shipments}.
 *
 * <p>O cliente informa só o que é dele: CEP, número, complemento e peso. Logradouro, bairro,
 * cidade e UF vêm da ViaCEP; o frete vem da tabela de preços. Ver {@code CreateShipmentCommand}.
 *
 * <p>{@code @Valid} no campo aninhado é obrigatório: sem ele, o Bean Validation não desce para
 * dentro de {@link AddressRequest} e as restrições de lá seriam ignoradas.
 *
 * @param origin      endereço de coleta
 * @param destination endereço de entrega
 * @param weightGrams peso do pacote em gramas (1 a 30.000)
 */
public record CreateShipmentRequest(
        @NotNull @Valid AddressRequest origin,
        @NotNull @Valid AddressRequest destination,

        @Schema(example = "1500", description = "Peso em gramas")
        @NotNull @Min(Weight.MIN_GRAMS) @Max(Weight.MAX_GRAMS)
        Integer weightGrams) {

    /**
     * @param postalCode CEP, com ou sem hífen
     * @param number     número (use "S/N" quando não houver)
     * @param complement complemento opcional
     */
    public record AddressRequest(
            @Schema(example = "01001-000")
            @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "deve estar no formato 00000-000")
            String postalCode,

            @Schema(example = "100")
            @NotBlank @Size(max = 20)
            String number,

            @Schema(example = "Sala 1")
            @Size(max = 100)
            String complement) {
    }
}
