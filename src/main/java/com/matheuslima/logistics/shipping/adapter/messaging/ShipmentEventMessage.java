package com.matheuslima.logistics.shipping.adapter.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Contrato de integração</b> dos eventos de envio: o JSON que trafega no RabbitMQ.
 *
 * <h2>Por que não publicar o evento de domínio direto?</h2>
 * Se {@code ShipmentEvent.Created} fosse serializado como está, qualquer refatoração no domínio
 * (renomear um campo, trocar um tipo) mudaria o JSON e quebraria consumidores que nem sabemos
 * que existem. O contrato de integração é a <b>linguagem publicada</b> (Published Language, DDD):
 * estável, versionada e documentada. O domínio evolui à vontade; o adaptador
 * ({@code OutboxShipmentEventPublisher}) traduz.
 *
 * <h2>Evolução do contrato</h2>
 * <ul>
 *   <li><b>Compatível</b> (não exige nova versão): adicionar campos ou novas chaves em
 *       {@code details}. Consumidores tolerantes ignoram o que não conhecem.</li>
 *   <li><b>Incompatível</b> (exige {@code schemaVersion} 2): remover ou renomear campos, mudar
 *       tipos ou significado. Em geral se publica v1 e v2 em paralelo até todos migrarem.</li>
 * </ul>
 *
 * <h2>Por que {@code details} é um mapa?</h2>
 * Cada tipo de evento tem dados próprios (o cancelamento tem motivo; a criação tem cidades). Um
 * mapa de Strings mantém o envelope fixo e deixa os detalhes flexíveis. As chaves de cada tipo
 * estão documentadas em {@code docs/07-mensageria.md}.
 *
 * <p>Este record fica num pacote neutro ({@code adapter.messaging}) porque é usado pelos dois
 * lados: o adaptador de saída (produz) e o de entrada (consome). Assim um não depende do outro.
 *
 * @param schemaVersion versão do contrato
 * @param eventId       ID único do evento (também vai como {@code messageId} AMQP)
 * @param eventType     tipo do evento, igual à routing key (ex.: {@code shipment.created})
 * @param occurredAt    quando o fato ocorreu (ISO-8601, UTC)
 * @param shipmentId    envio
 * @param customerId    dono do envio
 * @param status        status resultante
 * @param details       dados específicos do tipo de evento
 */
public record ShipmentEventMessage(
        int schemaVersion,
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID shipmentId,
        UUID customerId,
        String status,
        Map<String, String> details) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Tipos de evento publicados. Também são as routing keys no exchange de envios. */
    public static final class Types {
        public static final String CREATED = "shipment.created";
        public static final String DISPATCHED = "shipment.dispatched";
        public static final String DELIVERED = "shipment.delivered";
        public static final String CANCELLED = "shipment.cancelled";

        private Types() {
        }
    }
}
