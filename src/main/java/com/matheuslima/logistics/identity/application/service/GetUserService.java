package com.matheuslima.logistics.identity.application.service;

import com.matheuslima.logistics.identity.application.UserNotFoundException;
import com.matheuslima.logistics.identity.application.port.in.GetUserUseCase;
import com.matheuslima.logistics.identity.application.port.in.UserView;
import com.matheuslima.logistics.identity.application.port.out.UserRepository;
import com.matheuslima.logistics.identity.domain.UserId;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shared.application.TransactionRunner;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: consultar um usuário.
 *
 * <p>A checagem de visibilidade acontece <b>antes</b> de ir ao banco: se quem pede não é a
 * própria pessoa nem um operador, nem fazemos a consulta. Além de economizar I/O, a resposta é
 * idêntica (404) para "não existe" e "existe, mas não é seu".
 */
public final class GetUserService implements GetUserUseCase {

    private final UserRepository users;
    private final TransactionRunner transactions;

    public GetUserService(UserRepository users, TransactionRunner transactions) {
        this.users = Objects.requireNonNull(users);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public UserView get(Requester requester, UUID userId) {
        if (!requester.canAccessResourceOwnedBy(userId)) {
            throw new UserNotFoundException(userId);
        }
        return transactions.inReadOnlyTransaction(() -> users.findById(UserId.of(userId)))
                .map(UserView::from)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
