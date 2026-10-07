package com.matheuslima.logistics.identity.application.port.in;

/**
 * <b>Porta de entrada</b> (driving port): cadastrar um usuário.
 *
 * <h2>Por que uma interface para cada caso de uso?</h2>
 * <ul>
 *   <li><b>Fronteira explícita:</b> a interface é o contrato que os adaptadores de entrada
 *       (controller REST, runner de inicialização) enxergam. Eles não conhecem a classe que
 *       implementa, só o que o sistema <i>faz</i>.</li>
 *   <li><b>Testes de borda isolados:</b> nos testes do controller, a interface é substituída
 *       por um mock e o teste verifica só o HTTP (status, JSON, validação).</li>
 *   <li><b>Arquitetura que "grita" o domínio:</b> listar o pacote {@code port.in} mostra tudo o
 *       que o sistema faz, sem abrir nenhum arquivo (Screaming Architecture, Uncle Bob).</li>
 * </ul>
 *
 * <p>Regra adotada no projeto: <b>1 caso de uso = 1 porta de entrada = 1 serviço</b>. É fácil
 * de ensinar, de achar e de revisar.
 */
public interface RegisterUserUseCase {

    /**
     * @param command dados do cadastro
     * @return o usuário criado (sem dados sensíveis)
     * @throws com.matheuslima.logistics.identity.domain.EmailAlreadyRegisteredException se o
     *         e-mail já estiver em uso
     * @throws com.matheuslima.logistics.shared.domain.DomainValidationException se e-mail ou senha
     *         forem inválidos
     */
    UserView register(RegisterUserCommand command);
}
