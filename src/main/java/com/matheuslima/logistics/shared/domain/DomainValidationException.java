package com.matheuslima.logistics.shared.domain;

/**
 * Lançada quando um valor viola as regras de formação de um conceito do domínio
 * (ex.: e-mail malformado, peso acima do limite, CEP com 7 dígitos).
 *
 * <h2>Validação na borda E no domínio: não é duplicação?</h2>
 * São validações com propósitos diferentes:
 * <ul>
 *   <li><b>Bean Validation nos DTOs (borda HTTP):</b> rejeita cedo o que é obviamente inválido
 *       e devolve mensagens por campo, úteis para formulários. É uma conveniência do
 *       adaptador web.</li>
 *   <li><b>Value Objects (domínio):</b> garantem que um objeto inválido <i>nunca exista</i>,
 *       não importa por onde a chamada entrou (REST, mensageria, script de carga, teste).
 *       O domínio não confia nos adaptadores.</li>
 * </ul>
 * Se a borda esquecer uma regra, o domínio ainda protege a integridade. O inverso não é
 * verdade, e por isso o domínio é a fonte da verdade.
 *
 * <h2>E quando o valor é {@code null}?</h2>
 * Para {@code null} os Value Objects usam {@link java.util.Objects#requireNonNull}, que lança
 * {@link NullPointerException}. É uma escolha deliberada: a borda já rejeita campos ausentes,
 * então um {@code null} chegando ao domínio indica <b>bug</b> (contrato violado por código
 * nosso), e bug deve virar HTTP 500 e alerta, não um 400 que esconde o problema.
 */
public class DomainValidationException extends DomainException {

    /**
     * @param code    identificador estável do erro (ex.: {@code INVALID_EMAIL})
     * @param message descrição legível, segura para exibir ao cliente
     */
    public DomainValidationException(String code, String message) {
        super(ErrorCategory.INVALID_INPUT, code, message);
    }
}
