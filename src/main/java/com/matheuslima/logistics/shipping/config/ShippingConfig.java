package com.matheuslima.logistics.shipping.config;

import com.matheuslima.logistics.shared.application.TransactionRunner;
import com.matheuslima.logistics.shipping.application.port.in.ChangeShipmentStatusUseCase;
import com.matheuslima.logistics.shipping.application.port.in.CreateShipmentUseCase;
import com.matheuslima.logistics.shipping.application.port.in.GetShipmentUseCase;
import com.matheuslima.logistics.shipping.application.port.in.GetTrackingHistoryUseCase;
import com.matheuslima.logistics.shipping.application.port.in.ListShipmentsUseCase;
import com.matheuslima.logistics.shipping.application.port.in.RecordTrackingEventUseCase;
import com.matheuslima.logistics.shipping.application.port.out.PostalCodeLookup;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentEventPublisher;
import com.matheuslima.logistics.shipping.application.port.out.ShipmentRepository;
import com.matheuslima.logistics.shipping.application.port.out.TrackingHistoryStore;
import com.matheuslima.logistics.shipping.application.service.ChangeShipmentStatusService;
import com.matheuslima.logistics.shipping.application.service.CreateShipmentService;
import com.matheuslima.logistics.shipping.application.service.GetShipmentService;
import com.matheuslima.logistics.shipping.application.service.GetTrackingHistoryService;
import com.matheuslima.logistics.shipping.application.service.ListShipmentsService;
import com.matheuslima.logistics.shipping.application.service.RecordTrackingEventService;
import com.matheuslima.logistics.shipping.domain.freight.FreightCalculator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * <b>Composition root</b> do contexto de envios: registra os casos de uso e o domain service
 * como beans. Ver a explicação completa sobre {@code @Bean} versus {@code @Service} em
 * {@code IdentityConfig}.
 *
 * <h2>Leia esta classe como um mapa</h2>
 * Cada método mostra de quais portas um caso de uso depende. As implementações (adaptadores) são
 * escolhidas pelo Spring por tipo:
 * <ul>
 *   <li>{@link ShipmentRepository} → {@code ShipmentPersistenceAdapter} (JPA/PostgreSQL)</li>
 *   <li>{@link PostalCodeLookup} → {@code ViaCepPostalCodeLookup} (HTTP + cache + resiliência)</li>
 *   <li>{@link ShipmentEventPublisher} → {@code OutboxShipmentEventPublisher} (outbox → RabbitMQ)</li>
 *   <li>{@link TrackingHistoryStore} → {@code MongoTrackingHistoryStore} (MongoDB)</li>
 *   <li>{@link TransactionRunner} → {@code SpringTransactionRunner}</li>
 * </ul>
 * Trocar uma tecnologia é trocar um adaptador. Nenhum método desta classe muda, e nenhum caso de
 * uso também.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShippingProperties.class)
public class ShippingConfig {

    /**
     * O domain service também é um bean: ele é sem estado e a tabela de preços vem da
     * configuração. Assim existe uma única instância, configurada num só lugar.
     */
    @Bean
    FreightCalculator freightCalculator(ShippingProperties properties) {
        return new FreightCalculator(properties.freight().toFreightTable());
    }

    @Bean
    CreateShipmentUseCase createShipmentUseCase(ShipmentRepository shipments, PostalCodeLookup postalCodeLookup,
                                                ShipmentEventPublisher eventPublisher,
                                                FreightCalculator freightCalculator,
                                                TransactionRunner transactions, Clock clock) {
        return new CreateShipmentService(shipments, postalCodeLookup, eventPublisher, freightCalculator,
                transactions, clock);
    }

    @Bean
    ChangeShipmentStatusUseCase changeShipmentStatusUseCase(ShipmentRepository shipments,
                                                            ShipmentEventPublisher eventPublisher,
                                                            TransactionRunner transactions, Clock clock) {
        return new ChangeShipmentStatusService(shipments, eventPublisher, transactions, clock);
    }

    @Bean
    GetShipmentUseCase getShipmentUseCase(ShipmentRepository shipments, TransactionRunner transactions) {
        return new GetShipmentService(shipments, transactions);
    }

    @Bean
    ListShipmentsUseCase listShipmentsUseCase(ShipmentRepository shipments, TransactionRunner transactions) {
        return new ListShipmentsService(shipments, transactions);
    }

    @Bean
    GetTrackingHistoryUseCase getTrackingHistoryUseCase(ShipmentRepository shipments,
                                                        TrackingHistoryStore trackingHistory,
                                                        TransactionRunner transactions) {
        return new GetTrackingHistoryService(shipments, trackingHistory, transactions);
    }

    @Bean
    RecordTrackingEventUseCase recordTrackingEventUseCase(TrackingHistoryStore trackingHistory, Clock clock) {
        return new RecordTrackingEventService(trackingHistory, clock);
    }
}
