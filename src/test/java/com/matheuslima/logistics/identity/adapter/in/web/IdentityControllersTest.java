package com.matheuslima.logistics.identity.adapter.in.web;

import com.matheuslima.logistics.identity.application.InvalidCredentialsException;
import com.matheuslima.logistics.identity.application.UserNotFoundException;
import com.matheuslima.logistics.identity.application.port.in.AuthenticateUserCommand;
import com.matheuslima.logistics.identity.application.port.in.AuthenticateUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.GetUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserCommand;
import com.matheuslima.logistics.identity.application.port.in.RegisterUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.UserView;
import com.matheuslima.logistics.identity.domain.AccessToken;
import com.matheuslima.logistics.identity.domain.Email;
import com.matheuslima.logistics.identity.domain.EmailAlreadyRegisteredException;
import com.matheuslima.logistics.identity.domain.Role;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.support.TestJwt;
import com.matheuslima.logistics.support.WebAdapterTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do contrato HTTP de {@link UserController} e {@link AuthController}.
 */
@WebAdapterTest({UserController.class, AuthController.class})
@DisplayName("Controllers de identidade (contrato HTTP)")
class IdentityControllersTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegisterUserUseCase registerUser;
    @MockitoBean
    private GetUserUseCase getUser;
    @MockitoBean
    private AuthenticateUserUseCase authenticateUser;

    private final UserView ana = new UserView(UUID.randomUUID(), "ana@example.com", Role.CUSTOMER, NOW);

    @Test
    @DisplayName("POST /users é público: 201 com Location e sem expor senha/hash")
    void registers() throws Exception {
        given(registerUser.register(any())).willReturn(ana);

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@example.com\", \"password\": \"senha-forte-123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + ana.id()))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("mass assignment: enviar \"role\": \"OPERATOR\" no corpo não concede o papel")
    void cannotChooseOwnRole() throws Exception {
        given(registerUser.register(any())).willReturn(ana);

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ana@example.com", "password": "senha-forte-123", "role": "OPERATOR"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUser).register(command.capture());
        assertThat(command.getValue().role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("cadastro: 400 para dados inválidos e 409 para e-mail repetido")
    void registrationErrors() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nao-e-email\", \"password\": \"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));

        given(registerUser.register(any())).willThrow(new EmailAlreadyRegisteredException(Email.of("ana@example.com")));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@example.com\", \"password\": \"senha-forte-123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("POST /auth/token: 200 no formato OAuth (Bearer, expiresIn)")
    void issuesToken() throws Exception {
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
        given(authenticateUser.authenticate(new AuthenticateUserCommand("ana@example.com", "senha-forte-123")))
                .willReturn(new AccessToken("jwt.compacto.assinado", expiresAt));

        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@example.com\", \"password\": \"senha-forte-123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").value("jwt.compacto.assinado"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn", allOf(greaterThan(1700), lessThanOrEqualTo(1800))));
    }

    @Test
    @DisplayName("credenciais inválidas: 401 com WWW-Authenticate e mensagem genérica")
    void invalidCredentials() throws Exception {
        given(authenticateUser.authenticate(any())).willThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@example.com\", \"password\": \"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    @DisplayName("login com campos vazios: 400 sem chamar o caso de uso")
    void blankCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authenticateUser);
    }

    @Test
    @DisplayName("GET /users/{id}: o Requester vem do token (sub + papel)")
    void getUserResolvesRequesterFromToken() throws Exception {
        given(getUser.get(Requester.customer(ana.id()), ana.id())).willReturn(ana);
        UUID operatorId = UUID.randomUUID();
        given(getUser.get(Requester.operatorUser(operatorId), ana.id())).willReturn(ana);
        UUID stranger = UUID.randomUUID();
        given(getUser.get(Requester.customer(stranger), ana.id())).willThrow(new UserNotFoundException(ana.id()));

        mvc.perform(get("/api/v1/users/{id}", ana.id()).with(TestJwt.customer(ana.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.id().toString()));
        mvc.perform(get("/api/v1/users/{id}", ana.id()).with(TestJwt.operator(operatorId)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/{id}", ana.id()).with(TestJwt.customer(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("token com 'sub' que não é UUID: 401 (token inválido), não 500")
    void invalidSubjectIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/users/{id}", ana.id())
                        .with(jwt().jwt(token -> token.subject("not-a-uuid"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(getUser);
    }
}
