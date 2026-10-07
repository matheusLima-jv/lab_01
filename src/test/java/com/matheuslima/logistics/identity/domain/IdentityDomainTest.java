package com.matheuslima.logistics.identity.domain;

import com.matheuslima.logistics.shared.domain.DomainValidationException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do modelo de domínio do contexto de identidade.
 *
 * <p>Atenção especial aos testes de "não vazamento" ({@code toString} mascarado): são baratos
 * de escrever e protegem contra um dos incidentes de segurança mais comuns, que é a credencial
 * parar em log.
 */
@DisplayName("Domínio de identidade")
class IdentityDomainTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Nested
    @DisplayName("Email")
    class EmailTests {

        @Test
        @DisplayName("normaliza para minúsculas e remove espaços")
        void normalizes() {
            assertThat(Email.of("  Ana.Silva@Example.COM ").value()).isEqualTo("ana.silva@example.com");
        }

        @Test
        @DisplayName("normaliza de forma determinística mesmo com locale turco na JVM")
        void normalizationIsLocaleIndependent() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                // Com toLowerCase() sem Locale.ROOT, "I" viraria "ı" (i sem ponto) e o e-mail seria rejeitado.
                assertThat(Email.of("IGOR@EXAMPLE.COM").value()).isEqualTo("igor@example.com");
            } finally {
                Locale.setDefault(original);
            }
        }

        @ParameterizedTest(name = "rejeita \"{0}\"")
        @ValueSource(strings = {"", "sem-arroba", "a@b", "@example.com", "ana@", "ana silva@example.com",
                "ana@exa mple.com"})
        void rejectsInvalid(String input) {
            assertThatThrownBy(() -> Email.of(input))
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            ex -> assertThat(ex.code()).isEqualTo("INVALID_EMAIL"));
        }

        @Test
        @DisplayName("rejeita e-mails acima de 254 caracteres")
        void rejectsTooLong() {
            String local = "a".repeat(250);
            assertThatThrownBy(() -> Email.of(local + "@x.com")).isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("RawPassword")
    class RawPasswordTests {

        @Test
        @DisplayName("cadastro exige no mínimo 8 caracteres")
        void registrationRequiresMinimumLength() {
            assertThatThrownBy(() -> RawPassword.forRegistration("1234567"))
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            ex -> assertThat(ex.code()).isEqualTo("WEAK_PASSWORD"));
            assertThatThrownBy(() -> RawPassword.forRegistration("        "))
                    .isInstanceOf(DomainValidationException.class);
            assertThatCode(() -> RawPassword.forRegistration("12345678")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cadastro limita a 72 BYTES (limite do BCrypt), não 72 caracteres")
        void registrationLimitsBytesNotCharacters() {
            assertThatCode(() -> RawPassword.forRegistration("a".repeat(72))).doesNotThrowAnyException();
            // 37 x "ã" = 37 caracteres, mas 74 bytes em UTF-8.
            assertThatThrownBy(() -> RawPassword.forRegistration("ã".repeat(37)))
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            ex -> assertThat(ex.code()).isEqualTo("PASSWORD_TOO_LONG"));
        }

        @Test
        @DisplayName("login não aplica a política (senhas antigas continuam válidas)")
        void authenticationDoesNotApplyPolicy() {
            assertThat(RawPassword.forAuthentication("curta").reveal()).isEqualTo("curta");
        }

        @Test
        @DisplayName("toString nunca expõe a senha")
        void toStringIsMasked() {
            assertThat(RawPassword.forRegistration("super-secreta-123").toString())
                    .doesNotContain("super-secreta-123")
                    .contains("****");
        }
    }

    @Nested
    @DisplayName("User")
    class UserTests {

        private final PasswordHash hash = new PasswordHash("{bcrypt}$2a$10$abcdefghijklmnopqrstuv");

        @Test
        @DisplayName("register gera ID e mantém os dados informados")
        void register() {
            User user = User.register(Email.of("ana@example.com"), hash, Role.CUSTOMER, NOW);

            assertThat(user.id()).isNotNull();
            assertThat(user.email().value()).isEqualTo("ana@example.com");
            assertThat(user.role()).isEqualTo(Role.CUSTOMER);
            assertThat(user.isOperator()).isFalse();
            assertThat(user.createdAt()).isEqualTo(NOW);
            assertThat(user.passwordHash()).isEqualTo(hash);
        }

        @Test
        @DisplayName("igualdade por identidade e toString sem o hash")
        void identityAndSafeToString() {
            UserId id = UserId.newId();
            User a = User.restore(id, Email.of("ana@example.com"), hash, Role.OPERATOR, NOW);
            User b = User.restore(id, Email.of("outra@example.com"), hash, Role.CUSTOMER, NOW);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a.isOperator()).isTrue();
            assertThat(a.toString()).doesNotContain(hash.value()).contains(id.toString());
            assertThat(hash.toString()).doesNotContain("$2a$");
            assertThat(UserId.of(id.value())).hasToString(id.value().toString());
        }

        @Test
        @DisplayName("hash em branco é rejeitado")
        void blankHashIsRejected() {
            assertThatThrownBy(() -> new PasswordHash(" ")).isInstanceOf(IllegalArgumentException.class);
            assertThatNullPointerException().isThrownBy(() -> new PasswordHash(null));
        }
    }

    @Test
    @DisplayName("AccessToken não expõe o token no toString")
    void accessTokenIsMasked() {
        AccessToken token = new AccessToken("eyJhbGciOiJIUzI1NiJ9.payload.signature", NOW);

        assertThat(token.toString()).doesNotContain("eyJ").contains(NOW.toString());
    }

    @Test
    @DisplayName("EmailAlreadyRegisteredException é um conflito (409) com código estável")
    void emailAlreadyRegisteredIsConflict() {
        var exception = new EmailAlreadyRegisteredException(Email.of("ana@example.com"));

        assertThat(exception.category()).isEqualTo(ErrorCategory.CONFLICT);
        assertThat(exception.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(exception.getMessage()).contains("ana@example.com");
    }
}
