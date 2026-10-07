package com.matheuslima.logistics.identity.application.fake;

import com.matheuslima.logistics.identity.application.port.out.AccessTokenIssuer;
import com.matheuslima.logistics.identity.application.port.out.PasswordHasher;
import com.matheuslima.logistics.identity.application.port.out.UserRepository;
import com.matheuslima.logistics.identity.domain.AccessToken;
import com.matheuslima.logistics.identity.domain.Email;
import com.matheuslima.logistics.identity.domain.EmailAlreadyRegisteredException;
import com.matheuslima.logistics.identity.domain.PasswordHash;
import com.matheuslima.logistics.identity.domain.RawPassword;
import com.matheuslima.logistics.identity.domain.User;
import com.matheuslima.logistics.identity.domain.UserId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementações em memória das portas de saída do contexto de identidade.
 *
 * <p>Ficam juntas num arquivo porque são pequenas e só fazem sentido em conjunto. Cada uma
 * simula fielmente o contrato da porta. O repositório, por exemplo, rejeita e-mail duplicado
 * como faria a constraint UNIQUE do PostgreSQL.
 */
public final class IdentityFakes {

    private IdentityFakes() {
    }

    /** Repositório em memória que imita a constraint UNIQUE(email) do banco. */
    public static class InMemoryUserRepository implements UserRepository {

        private final Map<UserId, User> users = new LinkedHashMap<>();

        @Override
        public void save(User user) {
            boolean emailTakenByAnother = users.values().stream()
                    .anyMatch(existing -> existing.email().equals(user.email()) && !existing.equals(user));
            if (emailTakenByAnother) {
                throw new EmailAlreadyRegisteredException(user.email());
            }
            users.put(user.id(), user);
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return users.values().stream().filter(user -> user.email().equals(email)).findFirst();
        }

        @Override
        public boolean existsByEmail(Email email) {
            return findByEmail(email).isPresent();
        }

        public int count() {
            return users.size();
        }
    }

    /**
     * Hasher instantâneo e determinístico. NUNCA usar fora de testes: não é hash de verdade.
     * Conta as verificações para que os testes possam provar a mitigação de timing attack.
     */
    public static class FakePasswordHasher implements PasswordHasher {

        private static final String PREFIX = "fake-hash:";
        private int matchesCalls;

        @Override
        public PasswordHash hash(RawPassword password) {
            return new PasswordHash(PREFIX + password.reveal());
        }

        @Override
        public boolean matches(RawPassword password, PasswordHash hash) {
            matchesCalls++;
            return hash.value().equals(PREFIX + password.reveal());
        }

        public int matchesCalls() {
            return matchesCalls;
        }
    }

    /** Emite tokens previsíveis, fáceis de verificar nos testes. */
    public static class FakeAccessTokenIssuer implements AccessTokenIssuer {

        private final Instant expiresAt;

        public FakeAccessTokenIssuer(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        @Override
        public AccessToken issue(User user) {
            return new AccessToken("token-for-" + user.id() + "-" + user.role(), expiresAt);
        }
    }
}
