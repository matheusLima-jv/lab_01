package com.matheuslima.logistics.shared.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem está pedindo a operação: o usuário autenticado, reduzido ao que os casos de uso
 * precisam para decidir autorização.
 *
 * <h2>Por que não usar {@code Authentication} ou {@code Jwt} do Spring Security direto?</h2>
 * Isso amarraria a camada de aplicação ao Spring Security e ao formato do token. Com este
 * record, o adaptador web extrai os dados do JWT ({@code RequesterArgumentResolver}) e o núcleo
 * recebe um objeto simples, fácil de criar em testes ({@code Requester.customer(id)}). Trocar
 * JWT por sessão, API key ou mTLS não muda nenhum caso de uso.
 *
 * <h2>Por que a autorização por "dono do recurso" fica no caso de uso?</h2>
 * Regras como "cliente só vê os próprios envios" dependem de <i>dados</i> (quem é o dono do
 * envio), que só existem depois de carregar o agregado. O filtro de segurança HTTP não tem essa
 * informação. Por isso a checagem de papel (role) pode ficar na borda, mas a checagem de posse
 * (ownership) tem que estar no núcleo.
 *
 * @param userId   identificador do usuário autenticado (claim {@code sub} do JWT)
 * @param operator {@code true} se o usuário tem papel de operador (equipe interna)
 */
public record Requester(UUID userId, boolean operator) {

    public Requester {
        Objects.requireNonNull(userId, "userId must not be null");
    }

    /** Fábrica legível para testes e adaptadores: um cliente comum. */
    public static Requester customer(UUID userId) {
        return new Requester(userId, false);
    }

    /** Fábrica legível para testes e adaptadores: um operador da transportadora. */
    public static Requester operatorUser(UUID userId) {
        return new Requester(userId, true);
    }

    /**
     * Regra de visibilidade padrão: operadores enxergam tudo; os demais, apenas o que é deles.
     *
     * @param ownerId dono do recurso
     * @return {@code true} se quem chama pode ver/alterar o recurso
     */
    public boolean canAccessResourceOwnedBy(UUID ownerId) {
        return operator || userId.equals(ownerId);
    }
}
