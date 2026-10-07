package com.matheuslima.logistics.identity.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Usuário do sistema: <b>raiz de agregado</b> do contexto de identidade.
 *
 * <h2>Bounded contexts: por que "User" aqui e "Customer" no contexto de envios?</h2>
 * A mesma pessoa do mundo real é vista de formas diferentes por partes diferentes do negócio.
 * Para identidade, ela é um <i>usuário</i> com credenciais e papel. Para logística, é um
 * <i>cliente</i> dono de envios. Em DDD cada contexto tem o próprio modelo, e eles se
 * relacionam apenas pelo identificador (o {@code CustomerId} do contexto de envios carrega o
 * mesmo UUID deste {@link UserId}). Assim, mudar o cadastro de usuários não quebra a logística,
 * e vice-versa.
 *
 * <h2>Por que construtor privado + fábricas estáticas?</h2>
 * <ul>
 *   <li>{@link #register}: cria um usuário <i>novo</i> (gera ID, aplica regras de criação).</li>
 *   <li>{@link #restore}: <i>reconstitui</i> um usuário já existente a partir do banco, sem
 *       reaplicar regras de criação (ele já passou por elas quando foi criado).</li>
 * </ul>
 * Nomes de fábrica comunicam intenção melhor que um construtor sobrecarregado, e impedem que
 * alguém crie um usuário "pela metade".
 *
 * <h2>Por que não há setters?</h2>
 * Setters permitiriam qualquer código colocar o objeto em estado inválido. Mudanças de estado
 * acontecem por métodos com nome de negócio (ex.: um futuro {@code changePassword}). Este
 * agregado ainda não tem comportamento mutável porque nenhum caso de uso precisou: YAGNI.
 */
public final class User {

    private final UserId id;
    private final Email email;
    private final PasswordHash passwordHash;
    private final Role role;
    private final Instant createdAt;

    private User(UserId id, Email email, PasswordHash passwordHash, Role role, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * Cadastra um novo usuário.
     *
     * @param email        e-mail já validado (é um Value Object)
     * @param passwordHash hash já calculado pela porta {@code PasswordHasher}
     * @param role         papel concedido. Quem decide o papel é o caso de uso: o cadastro
     *                     público sempre concede {@link Role#CUSTOMER}.
     * @param now          instante atual, recebido de fora para o domínio não depender do
     *                     relógio do sistema (testes usam um relógio fixo)
     */
    public static User register(Email email, PasswordHash passwordHash, Role role, Instant now) {
        return new User(UserId.newId(), email, passwordHash, role, now);
    }

    /** Reconstitui um usuário persistido. Uso exclusivo de adaptadores de persistência. */
    public static User restore(UserId id, Email email, PasswordHash passwordHash, Role role, Instant createdAt) {
        return new User(id, email, passwordHash, role, createdAt);
    }

    public boolean isOperator() {
        return role == Role.OPERATOR;
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Entidades são iguais quando têm a mesma identidade, mesmo que outros atributos difiram
     * (é a diferença fundamental entre <i>entidade</i> e <i>Value Object</i>).
     */
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof User user && id.equals(user.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        // Sem o hash da senha, de propósito.
        return "User[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
