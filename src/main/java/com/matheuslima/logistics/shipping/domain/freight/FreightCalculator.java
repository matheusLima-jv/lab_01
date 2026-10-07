package com.matheuslima.logistics.shipping.domain.freight;

import com.matheuslima.logistics.shipping.domain.Address;
import com.matheuslima.logistics.shipping.domain.Money;
import com.matheuslima.logistics.shipping.domain.Weight;

import java.util.Objects;

/**
 * Calcula o frete de um envio: um <b>Domain Service</b>.
 *
 * <h2>O que é um Domain Service e por que usar um aqui?</h2>
 * Algumas regras de negócio não pertencem naturalmente a nenhuma entidade. O preço do frete
 * depende de dois endereços, de um peso e de uma tabela de preços. Colocá-lo dentro de
 * {@code Shipment} obrigaria o agregado a conhecer a tabela de preços, que tem ciclo de vida e
 * fonte próprios. Um Domain Service é a resposta do DDD para isso:
 * <ul>
 *   <li>é <b>sem estado</b> (a tabela é configuração imutável, não estado de negócio);</li>
 *   <li>fala a linguagem do domínio (recebe {@link Address} e {@link Weight}, devolve
 *       {@link Money});</li>
 *   <li>é Java puro, testado com testes unitários parametrizados, sem Spring.</li>
 * </ul>
 *
 * <p>Não confunda com "Application Service" (os casos de uso em {@code application.service}):
 * aquele orquestra (busca, chama portas, controla transação); este só contém regra de negócio.
 *
 * <h2>Fórmula</h2>
 * {@code frete = taxa base da rota + (valor por kg × quilos iniciados)}
 *
 * <p>Exemplo com a tabela padrão: 1,5 kg de São Paulo/SP para Rio de Janeiro/RJ (mesma região,
 * Sudeste) = R$ 25,00 + R$ 2,50 × 2 = R$ 30,00.
 */
public final class FreightCalculator {

    private final FreightTable table;

    public FreightCalculator(FreightTable table) {
        this.table = Objects.requireNonNull(table, "table must not be null");
    }

    public Money calculate(Address origin, Address destination, Weight weight) {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(weight, "weight must not be null");

        RouteType route = RouteType.between(origin, destination);
        Money baseFee = table.baseFee(route);
        Money weightFee = table.perKilogram().times(weight.kilogramsRoundedUp());
        return baseFee.plus(weightFee);
    }
}
