package com.matheuslima.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação.
 *
 * <h2>O que {@code @SpringBootApplication} faz</h2>
 * É a soma de três anotações:
 * <ul>
 *   <li>{@code @Configuration}: esta classe pode declarar beans;</li>
 *   <li>{@code @EnableAutoConfiguration}: o Spring Boot olha o classpath e configura o que
 *       encontrar (DataSource, JPA, MongoDB, RabbitMQ, Spring MVC, Security...), recuando
 *       sempre que nós definimos o bean ({@code @ConditionalOnMissingBean});</li>
 *   <li>{@code @ComponentScan}: registra como beans as classes anotadas
 *       ({@code @Component}, {@code @Repository}, {@code @RestController}...) neste pacote e nos
 *       subpacotes. Por isso esta classe fica na raiz {@code com.matheuslima.logistics}.</li>
 * </ul>
 *
 * <h2>Por que esta classe está "vazia"?</h2>
 * Tudo que for colocado aqui ({@code @EnableScheduling}, {@code @EnableCaching}, beans...) é
 * aplicado também em todos os testes de fatia ({@code @WebMvcTest}, {@code @DataJpaTest}), que
 * passariam a carregar infraestrutura que não deveriam. Cada {@code @Enable*} mora numa classe
 * {@code @Configuration} do seu assunto (ex.: {@code OutboxConfig}, {@code CacheConfig}).
 *
 * <h2>Onde ficam os beans dos casos de uso?</h2>
 * Em {@code IdentityConfig} e {@code ShippingConfig}: os serviços da camada de aplicação não têm
 * anotações do Spring, então são registrados explicitamente com {@code @Bean}. Ver
 * {@code docs/11-beans-e-configuracao.md}.
 */
@SpringBootApplication
public class LogisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsApplication.class, args);
    }
}
