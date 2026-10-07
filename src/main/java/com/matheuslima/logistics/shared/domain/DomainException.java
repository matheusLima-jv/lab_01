package com.matheuslima.logistics.shared.domain;

import java.util.Objects;

/**
 * Base para violações de regras de negócio detectadas <b>no domínio</b>.
 *
 * <h2>Por que exceções não checadas ({@link RuntimeException})?</h2>
 * Exceções checadas "vazam" pelas assinaturas de todas as camadas ({@code throws} em cascata)
 * e incentivam {@code try/catch} vazios só para o código compilar. Uma violação de regra de
 * negócio não é recuperável no ponto onde ocorre: ela sobe até o adaptador de entrada, que a
 * traduz para o protocolo dele (ex.: HTTP 409).
 *
 * <h2>Por que um {@code code} além da mensagem?</h2>
 * A mensagem é para pessoas e pode mudar (redação, idioma). O {@code code} é estável e pode
 * ser usado por clientes da API para decidir o que fazer (ex.: exibir uma tela específica
 * quando {@code code == "SHIPMENT_INVALID_STATUS_TRANSITION"}). Mudar um code é quebra de
 * contrato; mudar a mensagem não é.
 *
 * <h2>Por que separar de {@code ApplicationException}?</h2>
 * Clareza de origem: {@code DomainException} nasce de uma regra do modelo (invariante),
 * enquanto {@code ApplicationException} nasce da orquestração (recurso não encontrado,
 * dependência fora do ar). As duas compartilham {@link ErrorCategory}, o que permite ao
 * adaptador web tratá-las de forma uniforme.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCategory category;
    private final String code;

    /**
     * @param category categoria semântica, usada pelos adaptadores para escolher a resposta
     * @param code     identificador estável, em MAIÚSCULAS_COM_UNDERSCORE
     * @param message  descrição legível do problema (pode ser exibida ao cliente)
     */
    protected DomainException(ErrorCategory category, String code, String message) {
        super(message);
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public ErrorCategory category() {
        return category;
    }

    public String code() {
        return code;
    }
}
