package com.matheuslima.logistics.shared.infrastructure.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Liga a abstração de cache do Spring ({@code @Cacheable}, {@code @CacheEvict}...).
 *
 * <h2>Onde está a configuração do cache?</h2>
 * No {@code application.yml} ({@code spring.cache.*}). Com o Caffeine no classpath, o Spring Boot
 * cria um {@code CaffeineCacheManager} sozinho, com o cache {@code postal-codes} limitado a
 * 10.000 entradas e expiração de 24 h. Configuração declarativa é mais fácil de ajustar por
 * ambiente do que código.
 *
 * <h2>Por que Caffeine (cache local) e não Redis (cache distribuído)?</h2>
 * Dados de CEP são públicos, quase imutáveis e baratos de buscar de novo. Se cada instância
 * tiver seu próprio cache, o pior caso é cada uma consultar a ViaCEP uma vez por CEP, o que é
 * aceitável. Redis faria sentido se o cache precisasse ser consistente entre instâncias
 * (ex.: invalidação coordenada) ou se o custo de buscar fosse alto. Aqui seria mais infraestrutura
 * para operar sem ganho proporcional.
 *
 * <h2>Por que o {@code @EnableCaching} não fica na classe principal?</h2>
 * Anotações {@code @Enable*} na classe {@code @SpringBootApplication} são aplicadas também nos
 * testes de fatia ({@code @WebMvcTest}, {@code @DataJpaTest}), que então tentam montar
 * infraestrutura que não deveriam. Em uma classe {@code @Configuration} própria, só entram em
 * ação quando a configuração completa é carregada.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    /** Nome do cache de CEPs, referenciado por {@code @Cacheable} e pelo application.yml. */
    public static final String POSTAL_CODES = "postal-codes";
}
