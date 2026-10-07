package com.matheuslima.logistics.identity.application.port.in;

/**
 * Credenciais de login.
 *
 * @param email    e-mail informado (pode ser inválido; o serviço trata isso como credencial errada)
 * @param password senha em texto puro
 */
public record AuthenticateUserCommand(String email, String password) {

    /** Mascara a senha: o {@code toString} padrão de um record exibiria todos os campos. */
    @Override
    public String toString() {
        return "AuthenticateUserCommand[email=" + email + ", password=****]";
    }
}
