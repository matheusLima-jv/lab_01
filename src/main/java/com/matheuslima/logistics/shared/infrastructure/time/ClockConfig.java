package com.matheuslima.logistics.shared.infrastructure.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Fornece o {@link Clock} da aplicação como <b>bean</b>.
 *
 * <h2>Por que injetar o relógio em vez de chamar {@code Instant.now()}?</h2>
 * {@code Instant.now()} espalhado pelo código torna os testes não determinísticos: não dá para
 * afirmar "o envio foi criado às 10:00" se o horário vem do sistema. Com o relógio injetado, a
 * produção usa o relógio real e os testes usam {@code Clock.fixed(...)}. É um exemplo pequeno e
 * muito prático de inversão de dependência.
 *
 * <h2>Por que precisão de milissegundos ({@code Clock.tick})?</h2>
 * O relógio do sistema no Linux tem precisão de microssegundos ou nanossegundos. O PostgreSQL
 * guarda microssegundos e o MongoDB guarda milissegundos. Sem truncar, o mesmo instante "muda"
 * ao ir e voltar de cada banco, e comparações de igualdade falham de forma intermitente. Com
 * todos os instantes já em milissegundos, eles são representáveis sem perda nos dois bancos.
 *
 * <h2>Por que {@code proxyBeanMethods = false}?</h2>
 * Por padrão, classes {@code @Configuration} viram subclasses CGLIB para que um método
 * {@code @Bean} chamando outro devolva o mesmo singleton. Aqui nenhum método chama outro, então
 * o proxy é custo sem benefício (o próprio Spring Boot usa {@code false} em suas
 * auto-configurações). É o padrão de todas as configurações deste projeto.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * Relógio UTC com granularidade de 1 ms. UTC porque servidor não tem fuso: fuso é
     * preocupação de apresentação, aplicada na borda (no cliente) e não no armazenamento.
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
