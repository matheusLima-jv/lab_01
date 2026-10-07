package com.matheuslima.logistics.identity.domain;

import java.util.Objects;

/**
 * Hash de senha, opaco para o domínio. É a única forma de senha que persiste.
 *
 * <h2>Por que o domínio não calcula o hash?</h2>
 * O algoritmo (BCrypt, Argon2, PBKDF2) é detalhe técnico que muda com o tempo, à medida que
 * o hardware fica mais rápido. Quem calcula é a porta {@code PasswordHasher}, implementada na
 * infraestrutura com o {@code PasswordEncoder} do Spring Security. O domínio só sabe que existe
 * um hash e que ele deve ser tratado como segredo.
 *
 * <h2>Por que mascarar o {@code toString}?</h2>
 * Hash não é texto puro, mas vazar hashes facilita ataques offline (dicionário, rainbow tables
 * contra algoritmos fracos). Logs são copiados para muitos lugares; não queremos hashes lá.
 *
 * @param value hash no formato do codificador (ex.: {@code {bcrypt}$2a$10$...})
 */
public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[****]";
    }
}
