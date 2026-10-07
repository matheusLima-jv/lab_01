package com.matheuslima.logistics.shipping.application.port.in;

/**
 * As transições que um cliente da aplicação pode solicitar: um <b>tipo algébrico</b>
 * (sealed interface + records), recurso moderno do Java 21.
 *
 * <h2>Por que não um enum {@code DISPATCH, DELIVER, CANCEL}?</h2>
 * Porque {@code Cancel} carrega um dado ({@code reason}) e as outras não. Com enum, o motivo
 * viraria um campo opcional solto no comando, válido só às vezes, e alguém poderia mandar
 * "DISPATCH com motivo" ou "CANCEL sem motivo". Com tipos selados, cada variante tem
 * exatamente os dados de que precisa, e o {@code switch} no serviço desestrutura o record
 * ({@code case Cancel(String reason) -> ...}) com verificação de exaustividade pelo compilador.
 */
public sealed interface StatusChange {

    /** Coleta realizada: CREATED -> IN_TRANSIT (somente operadores). */
    record Dispatch() implements StatusChange {
    }

    /** Entrega realizada: IN_TRANSIT -> DELIVERED (somente operadores). */
    record Deliver() implements StatusChange {
    }

    /** Cancelamento: CREATED -> CANCELLED (dono do envio ou operador). */
    record Cancel(String reason) implements StatusChange {
    }
}
