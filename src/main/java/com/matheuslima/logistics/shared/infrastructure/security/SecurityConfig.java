package com.matheuslima.logistics.shared.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Configuração de segurança HTTP: <b>quem</b> pode chamar <b>o quê</b>.
 *
 * <h2>Modelo: API stateless com Bearer token (JWT)</h2>
 * Cada requisição traz {@code Authorization: Bearer <jwt>}. O filtro do Resource Server valida a
 * assinatura, a expiração e o emissor do token ({@code JwtDecoder} em {@link JwtConfig}) e popula
 * o {@code SecurityContext}. Não existe sessão no servidor: qualquer instância atende qualquer
 * requisição, o que simplifica a escala horizontal.
 *
 * <h2>Decisões, uma a uma</h2>
 * <ul>
 *   <li><b>CSRF desligado:</b> CSRF explora o envio <i>automático</i> de cookies pelo navegador.
 *       Esta API não usa cookies de autenticação; o token vai num header que o navegador nunca
 *       envia sozinho. Sem cookie, não há CSRF. (Se um dia o token for guardado em cookie, a
 *       proteção CSRF precisa voltar.)</li>
 *   <li><b>STATELESS:</b> o Spring Security não cria nem usa {@code HttpSession}.</li>
 *   <li><b>httpBasic/formLogin/logout desligados:</b> o login é o endpoint
 *       {@code POST /api/v1/auth/token}. Mecanismos que não usamos só aumentam a superfície de
 *       ataque.</li>
 *   <li><b>"Negar por padrão":</b> a última regra é {@code anyRequest().authenticated()}. Um
 *       endpoint novo nasce protegido; liberar acesso é que exige uma linha explícita.</li>
 *   <li><b>Papéis em dois níveis:</b> regras grossas aqui (por URL) e regras de papel por endpoint
 *       com {@code @PreAuthorize} ({@code @EnableMethodSecurity}). Regras que dependem de dados
 *       (dono do envio) ficam nos casos de uso.</li>
 * </ul>
 *
 * <h2>Cabeçalhos de segurança</h2>
 * O Spring Security já adiciona por padrão {@code X-Content-Type-Options: nosniff},
 * {@code X-Frame-Options: DENY}, {@code Cache-Control: no-store} (tokens e dados pessoais não
 * ficam em cache de proxies) e HSTS em conexões HTTPS.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final String[] API_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               JwtAuthenticationConverter jwtAuthenticationConverter,
                                               ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                               ProblemDetailAccessDeniedHandler accessDeniedHandler) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Cadastro e login: por definição, quem chama ainda não tem token.
                        .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/auth/token").permitAll()
                        // Probes do orquestrador e informações públicas da aplicação.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Documentação da API. Em produção pode ser restrita à rede interna.
                        .requestMatchers(API_DOCS).permitAll()
                        // Despacho de erros do container de servlets: sem isso, um erro em uma rota
                        // pública poderia virar 401 ao ser encaminhado para /error.
                        .requestMatchers("/error").permitAll()
                        // Métricas expõem detalhes internos: só para operadores.
                        .requestMatchers("/actuator/**").hasRole("OPERATOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /**
     * Converte a claim {@code roles} do token (ex.: {@code ["OPERATOR"]}) em authorities do Spring
     * Security ({@code ROLE_OPERATOR}). O prefixo {@code ROLE_} é a convenção que faz
     * {@code hasRole('OPERATOR')} funcionar. Por padrão, o conversor leria as claims
     * {@code scope}/{@code scp}, que não usamos.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenClaims.ROLES);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * CORS: quais origens de navegador podem chamar a API.
     *
     * <p>CORS não é proteção do servidor; é uma regra que o <i>navegador</i> aplica para proteger o
     * usuário. Chamadas de servidor para servidor (curl, Postman, outro backend) ignoram CORS.
     * Por isso a lista de origens vem de configuração e é vazia por padrão: nenhum site pode
     * chamar a API a partir do navegador do usuário até que alguém autorize explicitamente.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of(HttpHeaders.LOCATION, "X-Correlation-Id"));
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
