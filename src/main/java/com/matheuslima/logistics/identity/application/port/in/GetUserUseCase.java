package com.matheuslima.logistics.identity.application.port.in;

import com.matheuslima.logistics.shared.application.Requester;

import java.util.UUID;

/**
 * <b>Porta de entrada</b>: consultar um usuário pelo ID.
 *
 * <p>Regra de visibilidade: a própria pessoa ou um operador. Para os demais, a resposta é "não
 * encontrado" (404), e não "proibido" (403), para não revelar quais IDs existem.
 */
public interface GetUserUseCase {

    UserView get(Requester requester, UUID userId);
}
