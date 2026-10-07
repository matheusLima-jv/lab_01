package com.matheuslima.logistics.identity.adapter.in.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da conta de operador criada na inicialização ({@code app.bootstrap.operator.*}).
 *
 * <p>E-mail e senha não têm anotações de validação porque só são obrigatórios quando
 * {@code enabled=true}; essa checagem condicional é feita pelo {@link OperatorBootstrapRunner}.
 *
 * @param enabled  liga o bootstrap
 * @param email    e-mail do operador
 * @param password senha do operador (em produção, viria de um cofre de segredos)
 */
@ConfigurationProperties(prefix = "app.bootstrap.operator")
public record OperatorBootstrapProperties(boolean enabled, String email, String password) {

    @Override
    public String toString() {
        return "OperatorBootstrapProperties[enabled=" + enabled + ", email=" + email + ", password=****]";
    }
}
