package com.matheuslima.logistics.shared.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * Configuração dos tokens JWT ({@code app.security.jwt.*}).
 *
 * <h2>De onde vem o segredo</h2>
 * O {@code application.yml} <b>não</b> define o segredo. Ele vem da variável de ambiente
 * {@code APP_SECURITY_JWT_SECRET} (o Spring converte o nome para a propriedade
 * {@code app.security.jwt.secret} pelo <i>relaxed binding</i>). Se a variável não existir, a
 * aplicação não sobe. É melhor do que subir com um segredo padrão conhecido, que permitiria a
 * qualquer pessoa forjar tokens válidos. Só o perfil {@code local} e os testes têm um segredo
 * fixo, sempre marcado como "apenas desenvolvimento".
 *
 * <p>Para gerar um segredo: {@code openssl rand -base64 32}
 *
 * <h2>Por que exigir 256 bits?</h2>
 * O HS256 (HMAC-SHA256) usa uma chave do tamanho do hash, 256 bits. Chaves menores reduzem a
 * segurança e são rejeitadas pela biblioteca Nimbus. A validação abaixo transforma esse erro em
 * uma mensagem clara na subida.
 *
 * @param issuer         identificador do emissor (claim {@code iss}); tokens de outro emissor são
 *                       rejeitados
 * @param secret         chave HMAC em Base64, com pelo menos 32 bytes depois de decodificada
 * @param accessTokenTtl validade do token de acesso
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(@NotBlank String issuer, @NotBlank String secret, @NotNull Duration accessTokenTtl) {

    static final int MIN_SECRET_BYTES = 32;

    /** Monta a chave de assinatura, falhando com mensagem clara se o segredo for fraco ou inválido. */
    public SecretKey signingKey() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret.strip());
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException("app.security.jwt.secret must be Base64-encoded", notBase64);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must have at least 256 bits (32 bytes) after Base64 decoding; "
                            + "generate one with: openssl rand -base64 32");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /** Nunca exibir o segredo: records geram {@code toString} com todos os campos. */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", secret=****, accessTokenTtl=" + accessTokenTtl + "]";
    }
}
