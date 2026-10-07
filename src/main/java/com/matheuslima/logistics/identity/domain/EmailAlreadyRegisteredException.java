package com.matheuslima.logistics.identity.domain;

import com.matheuslima.logistics.shared.domain.DomainException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;

/**
 * Violação da regra "um e-mail pertence a no máximo uma conta" (HTTP 409).
 *
 * <h2>Por que essa regra é garantida em dois lugares?</h2>
 * O caso de uso consulta {@code existsByEmail} antes de salvar, o que dá uma resposta rápida e
 * clara no caso comum. Mas consultar e depois inserir não é atômico (<i>check-then-act</i>):
 * duas requisições simultâneas com o mesmo e-mail passam as duas pela checagem. A garantia real
 * é a constraint UNIQUE do PostgreSQL; o adaptador de persistência traduz a violação dela para
 * esta mesma exceção. Resultado: o cliente recebe 409 nos dois cenários, nunca um 500.
 *
 * <p><b>Nota de segurança:</b> responder "e-mail já cadastrado" permite descobrir quem tem conta
 * (enumeração de usuários). É um trade-off aceito por muitos produtos em nome da usabilidade. Em
 * sistemas sensíveis, a alternativa é responder sempre "verifique seu e-mail" e tratar o caso
 * por mensagem. Fica documentado como decisão consciente.
 */
public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException(Email email) {
        super(ErrorCategory.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "Já existe uma conta cadastrada com o e-mail %s.".formatted(email.value()));
    }
}
