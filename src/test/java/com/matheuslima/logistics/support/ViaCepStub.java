package com.matheuslima.logistics.support;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * ViaCEP falsa para os testes de ponta a ponta: um único servidor WireMock compartilhado.
 *
 * <h2>Por que um singleton e não um {@code @RegisterExtension} por classe?</h2>
 * O Spring guarda contextos de teste em cache. Se cada classe de teste subisse o próprio WireMock
 * (numa porta aleatória diferente), o contexto em cache continuaria apontando para a porta da
 * primeira classe, e as seguintes falhariam de forma misteriosa. Com um servidor único, iniciado
 * uma vez e encerrado junto com a JVM, a URL é a mesma durante toda a execução.
 */
public final class ViaCepStub {

    private static final WireMockServer SERVER = startServer();

    private ViaCepStub() {
    }

    private static WireMockServer startServer() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }

    public static String baseUrl() {
        return SERVER.baseUrl();
    }

    /** Programa a resposta de um CEP existente, no formato real da ViaCEP. */
    public static void stubAddress(String cep, String street, String district, String city, String uf) {
        SERVER.stubFor(get(path(cep)).willReturn(okJson("""
                {"cep": "%s-%s", "logradouro": "%s", "complemento": "", "bairro": "%s",
                 "localidade": "%s", "uf": "%s", "ibge": "0000000"}
                """.formatted(cep.substring(0, 5), cep.substring(5), street, district, city, uf))));
    }

    /** Programa a resposta de CEP inexistente ({@code {"erro": "true"}} com HTTP 200). */
    public static void stubNotFound(String cep) {
        SERVER.stubFor(get(path(cep)).willReturn(okJson("{\"erro\": \"true\"}")));
    }

    /** Programa uma falha de servidor (HTTP 500) para o CEP. */
    public static void stubServerError(String cep) {
        SERVER.stubFor(get(path(cep)).willReturn(aResponse().withStatus(500)));
    }

    /** Quantas vezes a aplicação consultou este CEP (para verificar o cache). */
    public static int requestsFor(String cep) {
        return SERVER.findAll(getRequestedFor(urlEqualTo(path(cep)))).size();
    }

    private static String path(String cep) {
        return "/ws/" + cep + "/json/";
    }
}
