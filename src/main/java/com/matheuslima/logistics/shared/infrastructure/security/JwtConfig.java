package com.matheuslima.logistics.shared.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Beans criptográficos: codificador/decodificador de JWT e codificador de senhas.
 *
 * <h2>Por que JWT autoassinado com HS256 (chave simétrica)?</h2>
 * Esta API é ao mesmo tempo quem emite e quem valida os tokens. Com uma única parte envolvida,
 * HMAC é simples e seguro: a mesma chave secreta assina e verifica. Quando outros serviços
 * precisarem validar os tokens, o caminho é trocar para RS256/ES256 (chave assimétrica): só o
 * emissor tem a chave privada e os demais validam com a chave pública, publicada num endpoint
 * JWKS. Em empresas, normalmente esse papel é de um Identity Provider dedicado (Keycloak, Auth0,
 * Cognito, Entra ID), e esta aplicação viraria só Resource Server. Ver ADR 0003.
 *
 * <h2>Por que usar o Nimbus do Spring Security em vez de escrever um filtro JWT próprio?</h2>
 * Validação de token é código de segurança: comparar assinatura em tempo constante, rejeitar
 * {@code alg: none}, checar expiração com tolerância de relógio, validar emissor... O
 * {@code NimbusJwtDecoder} faz tudo isso e é mantido e auditado pela comunidade. Escrever um
 * {@code OncePerRequestFilter} "na mão" com uma biblioteca JWT qualquer é um padrão comum em
 * tutoriais e uma fonte comum de vulnerabilidades.
 *
 * <h2>Por que o {@code DelegatingPasswordEncoder}?</h2>
 * Ele grava o algoritmo junto com o hash ({@code {bcrypt}$2a$10$...}). Se amanhã a política mudar
 * para Argon2, os hashes antigos continuam válidos (o prefixo diz como verificá-los) e os novos já
 * saem com o algoritmo novo. Sem o prefixo, trocar de algoritmo exigiria resetar a senha de todo
 * mundo.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(properties.signingKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * O decodificador valida a assinatura (HS256 apenas: tokens com outro algoritmo são
     * rejeitados), a expiração ({@code exp}, com 60 s de tolerância para diferenças de relógio
     * entre máquinas) e o emissor ({@code iss}).
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
