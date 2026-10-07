package com.matheuslima.logistics.identity.adapter.in.web;

import com.matheuslima.logistics.identity.application.port.in.AuthenticateUserCommand;
import com.matheuslima.logistics.identity.application.port.in.AuthenticateUserUseCase;
import com.matheuslima.logistics.identity.domain.AccessToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

/**
 * <b>Adaptador de entrada REST</b> da autenticação.
 *
 * <h2>Por que {@code POST /auth/token} e não {@code POST /login}?</h2>
 * Em REST, URLs nomeiam recursos (substantivos). O login <i>emite um token</i>, então o recurso é
 * "token". O nome segue o endpoint de token do OAuth 2.0, que qualquer desenvolvedor reconhece.
 *
 * <h2>Por que 200 e não 201?</h2>
 * O token não vira um recurso consultável na API (não existe {@code GET /auth/token/{id}}), então
 * não há {@code Location} a devolver. O próprio OAuth 2.0 responde 200 no endpoint de token.
 *
 * <h2>E o cache?</h2>
 * Respostas com credenciais não podem ficar em cache (RFC 6749 exige {@code Cache-Control:
 * no-store}). O Spring Security já envia esse header em todas as respostas.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Emissão de tokens de acesso (JWT)")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUser;
    private final Clock clock;

    public AuthController(AuthenticateUserUseCase authenticateUser, Clock clock) {
        this.authenticateUser = authenticateUser;
        this.clock = clock;
    }

    @PostMapping("/token")
    @SecurityRequirements
    @Operation(summary = "Autentica e emite um token de acesso",
            description = "Use o accessToken no header Authorization: Bearer <token>.")
    @ApiResponse(responseCode = "200", description = "Token emitido")
    @ApiResponse(responseCode = "400", description = "E-mail ou senha ausentes ou longos demais")
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request) {
        AccessToken token = authenticateUser.authenticate(new AuthenticateUserCommand(request.email(), request.password()));
        // Tempo restante medido agora, e não o TTL configurado: a RFC 6749 conta o expires_in a
        // partir da geração da resposta. O truncamento (1799,9 s vira 1799) erra para o lado
        // seguro, porque o cliente renova o token um pouco antes, nunca depois.
        long expiresIn = Math.max(0, Duration.between(clock.instant(), token.expiresAt()).toSeconds());
        return new TokenResponse(token.value(), TokenResponse.BEARER, expiresIn, token.expiresAt());
    }
}
