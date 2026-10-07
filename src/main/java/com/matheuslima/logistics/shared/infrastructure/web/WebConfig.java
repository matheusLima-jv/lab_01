package com.matheuslima.logistics.shared.infrastructure.web;

import com.matheuslima.logistics.shared.infrastructure.security.RequesterArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Personalizações do Spring MVC.
 *
 * <p>Implementar {@link WebMvcConfigurer} <b>acrescenta</b> configurações à auto-configuração do
 * Spring Boot, sem substituí-la. Já anotar uma classe com {@code @EnableWebMvc} desligaria a
 * auto-configuração inteira (conversores, tratamento de erros, recursos estáticos...), um erro
 * comum em projetos Spring Boot.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    /**
     * Registra o resolvedor que permite declarar {@code Requester requester} como parâmetro de
     * qualquer endpoint (ver {@link RequesterArgumentResolver}).
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequesterArgumentResolver());
    }
}
