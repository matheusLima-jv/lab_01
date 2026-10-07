package com.matheuslima.logistics.shipping.application.service;

import com.matheuslima.logistics.shared.application.TransactionRunner;
import com.matheuslima.logistics.shipping.application.PostalCodeNotFoundException;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentCommand;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentCommand.AddressInput;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ShipmentView;
import com.matheuslima.logistics.shipping.application.port.out.PostalCodeLookup;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentEventPublisher;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentRepository;
import com.matheuslima.logistics.shipping.domain.Address;
import com.matheuslima.logistics.shipping.domain.CustomerId;
import com.matheuslima.logistics.shipping.domain.Money;
import com.matheuslima.logistics.shipping.domain.PostalCode;
import com.matheuslima.logistics.shipping.domain.Shipment;
import com.matheuslima.logistics.shipping.domain.Weight;
import com.matheuslima.logistics.shipping.domain.freight.FreightCalculator;

import java.time.Clock;
import java.util.Objects;

/**
 * Caso de uso: registrar um envio.
 *
 * <h2>A ordem dos passos é uma decisão de arquitetura</h2>
 * <ol>
 *   <li><b>Validar o que é barato primeiro</b> (peso, formato dos CEPs): se a entrada for
 *       inválida, falhamos sem gastar nenhuma chamada de rede.</li>
 *   <li><b>Chamar a API externa FORA da transação.</b> A ViaCEP pode levar segundos (ou cair).
 *       Dentro de uma transação, cada requisição seguraria uma conexão do pool durante essa
 *       espera; com tráfego e uma ViaCEP lenta, o pool se esgota e a aplicação inteira para,
 *       inclusive endpoints que nem usam CEP. É um dos incidentes de produção mais comuns em
 *       sistemas que integram com terceiros.</li>
 *   <li><b>Aplicar a regra de negócio</b> (frete) no domain service: puro, rápido, sem I/O.</li>
 *   <li><b>Transação curta</b> só para gravar o envio e os eventos (outbox), que precisam ser
 *       atômicos entre si.</li>
 * </ol>
 *
 * <h2>Evolução possível</h2>
 * As duas consultas de CEP são independentes e poderiam rodar em paralelo (por exemplo, com
 * virtual threads), cortando a latência pela metade. Não fizemos para manter o fluxo linear e
 * fácil de ler; o cache de CEP já elimina a maior parte das chamadas repetidas.
 */
public final class CreateShipmentService implements CreateShipmentUseCase {

    private final ShipmentRepository shipments;
    private final PostalCodeLookup postalCodeLookup;
    private final ShipmentEventPublisher eventPublisher;
    private final FreightCalculator freightCalculator;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CreateShipmentService(ShipmentRepository shipments, PostalCodeLookup postalCodeLookup,
                                 ShipmentEventPublisher eventPublisher, FreightCalculator freightCalculator,
                                 TransactionRunner transactions, Clock clock) {
        this.shipments = Objects.requireNonNull(shipments);
        this.postalCodeLookup = Objects.requireNonNull(postalCodeLookup);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.freightCalculator = Objects.requireNonNull(freightCalculator);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ShipmentView create(CreateShipmentCommand command) {
        // 1) Validações baratas, sem I/O.
        CustomerId owner = CustomerId.of(command.requester().userId());
        Weight weight = Weight.ofGrams(command.weightGrams());
        PostalCode originCep = PostalCode.of(command.origin().postalCode());
        PostalCode destinationCep = PostalCode.of(command.destination().postalCode());

        // 2) Integração externa, fora de qualquer transação.
        Address origin = resolveAddress(originCep, command.origin(), "origem");
        Address destination = resolveAddress(destinationCep, command.destination(), "destino");

        // 3) Regra de negócio pura.
        Money freight = freightCalculator.calculate(origin, destination, weight);

        // 4) Transação curta: estado + eventos gravados atomicamente.
        return transactions.inTransaction(() -> {
            Shipment shipment = Shipment.create(owner, origin, destination, weight, freight, clock.instant());
            shipments.save(shipment);
            eventPublisher.publish(shipment.pullDomainEvents());
            return ShipmentView.from(shipment);
        });
    }

    private Address resolveAddress(PostalCode postalCode, AddressInput input, String role) {
        return postalCodeLookup.lookup(postalCode)
                .map(details -> details.toAddress(input.number(), input.complement()))
                .orElseThrow(() -> new PostalCodeNotFoundException(postalCode, role));
    }
}
