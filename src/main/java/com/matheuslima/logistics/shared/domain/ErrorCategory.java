package com.matheuslima.logistics.shared.domain;

/**
 * Categoria semântica de uma falha <b>esperada</b> (de negócio), independente de protocolo.
 *
 * <h2>Por que categorias em vez de colocar o status HTTP na exceção?</h2>
 * O núcleo da aplicação (domínio + casos de uso) não conhece HTTP. Ele é chamado por um
 * controller REST, mas também por um listener do RabbitMQ e por um runner de inicialização.
 * A exceção informa <i>que tipo</i> de problema aconteceu; cada adaptador traduz isso para o seu
 * protocolo. O adaptador web converte em status HTTP em um único lugar
 * ({@code GlobalExceptionHandler}), então mudar a tradução não exige tocar no domínio.
 *
 * <p>Esse é o princípio da <b>inversão de dependência</b> aplicado a erros: o núcleo define o
 * vocabulário e a borda se adapta a ele.
 *
 * <p>O que NÃO entra aqui: bugs. Um {@code NullPointerException} ou um estado impossível é falha
 * de programação e deve virar HTTP 500 e alerta, não uma categoria "tratável".
 */
public enum ErrorCategory {

    /** Entrada inválida: formato errado, valor fora da faixa permitida. HTTP 400. */
    INVALID_INPUT,

    /** Quem chama não se identificou ou as credenciais não conferem. HTTP 401. */
    UNAUTHENTICATED,

    /** Quem chama está identificado, mas não pode executar a operação. HTTP 403. */
    FORBIDDEN,

    /** O recurso não existe ou não é visível para quem chama. HTTP 404. */
    NOT_FOUND,

    /** A operação conflita com o estado atual (transição inválida, e-mail já usado). HTTP 409. */
    CONFLICT,

    /** Entrada bem formada, mas impossível de processar (ex.: CEP que não existe). HTTP 422. */
    UNPROCESSABLE,

    /** Uma dependência externa está indisponível; tentar de novo depois pode funcionar. HTTP 503. */
    UNAVAILABLE
}
