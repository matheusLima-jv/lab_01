package com.matheuslima.logistics.shipping;

import com.matheuslima.logistics.shipping.domain.Address;
import com.matheuslima.logistics.shipping.domain.BrazilianState;
import com.matheuslima.logistics.shipping.domain.CustomerId;
import com.matheuslima.logistics.shipping.domain.Money;
import com.matheuslima.logistics.shipping.domain.PostalCode;
import com.matheuslima.logistics.shipping.domain.Shipment;
import com.matheuslima.logistics.shipping.domain.Weight;
import com.matheuslima.logistics.shipping.domain.freight.FreightTable;

import java.time.Instant;
import java.util.UUID;

/**
 * Dados de teste reutilizáveis do contexto de envios (padrão <b>Object Mother</b>).
 *
 * <h2>Por que centralizar dados de teste?</h2>
 * Sem isso, cada teste monta seus próprios endereços "na mão". Quando o construtor de
 * {@link Address} ganhar um campo, dezenas de testes quebram por um motivo que não tem nada a ver
 * com o que eles verificam. Com um Object Mother, a mudança é feita em um lugar só, e os testes
 * ficam focados no comportamento ("dado um envio de SP para o RJ...") e não na montagem.
 *
 * <p>Os endereços são <b>reais</b> (consultados na ViaCEP), o que torna os testes e a
 * documentação coerentes com o que a API externa devolve de verdade.
 */
public final class ShippingFixtures {

    /** Instante fixo: testes determinísticos não dependem do relógio da máquina. */
    public static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private ShippingFixtures() {
    }

    /** Praça da Sé, São Paulo/SP (Sudeste). */
    public static Address saoPauloSe() {
        return new Address(PostalCode.of("01001-000"), "Praça da Sé", "100", "Sala 1", "Sé", "São Paulo",
                BrazilianState.SP);
    }

    /** Avenida Paulista, São Paulo/SP: mesma cidade de {@link #saoPauloSe()}. */
    public static Address saoPauloPaulista() {
        return new Address(PostalCode.of("01310-100"), "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo",
                BrazilianState.SP);
    }

    /** Campinas/SP: mesmo estado de São Paulo, cidade diferente. */
    public static Address campinas() {
        return new Address(PostalCode.of("13015-904"), "Avenida Anchieta", "200", null, "Centro", "Campinas",
                BrazilianState.SP);
    }

    /** Rio de Janeiro/RJ: mesma região (Sudeste) de São Paulo. */
    public static Address rioDeJaneiro() {
        return new Address(PostalCode.of("20040-020"), "Praça Pio X", "10", null, "Centro", "Rio de Janeiro",
                BrazilianState.RJ);
    }

    /** Manaus/AM: região Norte, inter-regional em relação a São Paulo. */
    public static Address manaus() {
        return new Address(PostalCode.of("69005-010"), "Rua Barão de São Domingos", "50", null, "Centro", "Manaus",
                BrazilianState.AM);
    }

    /** Tabela de frete padrão (mesmos valores do application.yml). */
    public static FreightTable defaultFreightTable() {
        return new FreightTable(
                Money.brl("12.00"),
                Money.brl("18.00"),
                Money.brl("25.00"),
                Money.brl("35.00"),
                Money.brl("2.50"));
    }

    public static CustomerId aCustomer() {
        return CustomerId.of(UUID.randomUUID());
    }

    /** Um envio recém-criado (SP -> RJ, 1,5 kg), já sem os eventos de criação pendentes. */
    public static Shipment newShipment(CustomerId owner) {
        Shipment shipment = Shipment.create(owner, saoPauloSe(), rioDeJaneiro(), Weight.ofGrams(1_500),
                Money.brl("30.00"), NOW);
        shipment.pullDomainEvents();
        return shipment;
    }
}
