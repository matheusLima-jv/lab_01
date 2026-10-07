package com.matheuslima.logistics.shipping.config;

import com.matheuslima.logistics.shipping.adapter.out.viacep.ViaCepPostalCodeLookup;
import com.matheuslima.logistics.shipping.adapter.out.viacep.ViaCepProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.ProxySelector;

/**
 * Cria o cliente HTTP dedicado à ViaCEP.
 *
 * <h2>Por que um {@code RestClient} por integração?</h2>
 * Cada API externa tem URL, timeouts, headers e às vezes autenticação próprios. Um cliente
 * compartilhado acabaria com a configuração "do pior caso" para todos. Um bean por integração,
 * com nome ({@link ViaCepPostalCodeLookup#REST_CLIENT_BEAN}), mantém cada uma isolada.
 *
 * <h2>{@code RestClient.Builder} é um bean com escopo <i>prototype</i></h2>
 * O Spring Boot registra o {@code RestClient.Builder} com escopo prototype: cada injeção recebe
 * uma instância nova. É necessário porque o builder é mutável. Se fosse singleton, configurar a
 * URL da ViaCEP nele "vazaria" para o próximo cliente construído com o mesmo builder. É um bom
 * exemplo de quando o escopo padrão (singleton) não serve. O builder já vem com os conversores
 * JSON e as métricas/observabilidade do Spring Boot aplicados.
 *
 * <h2>Por que o cliente HTTP do próprio JDK?</h2>
 * O {@code java.net.http.HttpClient} (Java 11+) suporta HTTP/2, funciona bem com virtual threads
 * e não exige dependência extra. Apache HttpClient ou Jetty seriam alternativas quando se precisa
 * de pool de conexões configurável em detalhe.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ViaCepProperties.class)
public class ViaCepClientConfig {

    @Bean(ViaCepPostalCodeLookup.REST_CLIENT_BEAN)
    public RestClient viaCepRestClient(RestClient.Builder builder, ViaCepProperties properties) {
        var requestFactory = ClientHttpRequestFactoryBuilder.jdk()
                // Respeita -Dhttps.proxyHost/-Dhttps.proxyPort: comum em redes corporativas.
                // Sem proxy configurado, o seletor padrão conecta direto.
                .withProxySelector(ProxySelector.getDefault())
                .build(HttpClientSettings.defaults()
                        .withTimeouts(properties.connectTimeout(), properties.readTimeout()));

        return builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // Identificar-se é cortesia com APIs públicas e ajuda o provedor a nos contatar.
                .defaultHeader(HttpHeaders.USER_AGENT, "logistics-api/1.0")
                .build();
    }
}
