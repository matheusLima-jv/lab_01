package com.matheuslima.logistics.shared.application;

import com.matheuslima.logistics.shared.domain.ErrorCategory;

import java.util.Objects;

/**
 * Base para falhas esperadas que nascem na <b>camada de aplicação</b> (orquestração),
 * e não em uma regra do modelo de domínio.
 *
 * <p>Exemplos: "envio não encontrado" (o domínio não sabe buscar nada, quem busca é o caso de
 * uso), "credenciais inválidas", "ViaCEP fora do ar".
 *
 * <p>Tem o mesmo formato de {@link com.matheuslima.logistics.shared.domain.DomainException}
 * (categoria + código + mensagem) para que os adaptadores tratem as duas famílias de forma
 * uniforme. Veja a explicação completa sobre categorias em {@link ErrorCategory}.
 */
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCategory category;
    private final String code;

    protected ApplicationException(ErrorCategory category, String code, String message) {
        this(category, code, message, null);
    }

    /**
     * Construtor com causa: preserva a exceção técnica original (ex.: timeout de rede) para
     * os logs, sem expô-la ao cliente. Encadear a causa é o que permite investigar um
     * incidente depois; descartá-la é um dos erros mais comuns em tratamento de exceções.
     */
    protected ApplicationException(ErrorCategory category, String code, String message, Throwable cause) {
        super(message, cause);
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
