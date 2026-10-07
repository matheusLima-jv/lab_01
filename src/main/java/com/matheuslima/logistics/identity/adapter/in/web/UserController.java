package com.matheuslima.logistics.identity.adapter.in.web;

import com.matheuslima.logistics.identity.application.port.in.GetUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserCommand;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.UserView;
import com.matheuslima.logistics.identity.domain.Role;
import com.matheuslima.logistics.shared.application.Requester;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * <b>Adaptador de entrada REST</b> do recurso "usuários".
 *
 * <h2>O papel de um controller na arquitetura hexagonal</h2>
 * Traduzir HTTP para casos de uso e de volta, e nada além disso: recebe o DTO, monta o Command,
 * chama a porta de entrada e converte a view em resposta HTTP. Sem regra de negócio, sem acesso a
 * repositório, sem {@code try/catch} (erros vão para o {@code GlobalExceptionHandler}). Um
 * controller "gordo" é o primeiro sintoma de uma arquitetura que está vazando.
 *
 * <h2>REST: cadastro é a criação de um recurso</h2>
 * {@code POST /api/v1/users} cria um recurso "usuário" e responde {@code 201 Created} com o header
 * {@code Location} apontando para o recurso criado ({@code /api/v1/users/{id}}), como manda a
 * RFC 9110. O {@code Location} é relativo de propósito: a RFC permite, e um caminho relativo
 * continua correto atrás de proxies e load balancers, onde host e porta externos são outros.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuários", description = "Cadastro e consulta de usuários")
public class UserController {

    private final RegisterUserUseCase registerUser;
    private final GetUserUseCase getUser;

    public UserController(RegisterUserUseCase registerUser, GetUserUseCase getUser) {
        this.registerUser = registerUser;
        this.getUser = getUser;
    }

    @PostMapping
    @SecurityRequirements // público: remove o cadeado deste endpoint no Swagger
    @Operation(summary = "Cadastra um cliente", description = "Cria um usuário com papel CUSTOMER.")
    @ApiResponse(responseCode = "201", description = "Usuário criado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        UserView user = registerUser.register(
                new RegisterUserCommand(request.email(), request.password(), Role.CUSTOMER));
        URI location = UriComponentsBuilder.fromPath("/api/v1/users/{id}").buildAndExpand(user.id()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um usuário", description = "Visível para a própria pessoa e para operadores.")
    @ApiResponse(responseCode = "200", description = "Usuário encontrado")
    @ApiResponse(responseCode = "404", description = "Inexistente ou não visível para quem consulta")
    public UserResponse get(@PathVariable UUID id, Requester requester) {
        return UserResponse.from(getUser.get(requester, id));
    }
}
