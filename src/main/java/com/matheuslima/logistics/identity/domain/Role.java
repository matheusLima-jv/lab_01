package com.matheuslima.logistics.identity.domain;

/**
 * Papéis de acesso. Mantidos deliberadamente pequenos: dois papéis cobrem os casos de uso.
 *
 * <ul>
 *   <li>{@link #CUSTOMER}: cliente que cria e acompanha os próprios envios.</li>
 *   <li>{@link #OPERATOR}: equipe interna da transportadora; enxerga todos os envios e é quem
 *       move um envio pelo fluxo físico (despacho, entrega).</li>
 * </ul>
 *
 * <h2>Por que papéis (RBAC) e não permissões finas?</h2>
 * RBAC é simples de entender, auditar e testar. Permissões finas (ex.: {@code shipment:dispatch})
 * valem a pena quando há muitos papéis combinando capacidades diferentes. A evolução é
 * natural: o JWT passaria a carregar uma claim {@code scope} e o Spring Security já sabe
 * convertê-la em authorities.
 *
 * <p>O nome é gravado como texto no banco e no token. Renomear uma constante é migração de
 * dados, não um refactor inofensivo.
 */
public enum Role {
    CUSTOMER,
    OPERATOR
}
