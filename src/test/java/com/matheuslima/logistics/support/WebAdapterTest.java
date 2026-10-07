package com.matheuslima.logistics.support;

import com.matheuslima.logistics.shared.infrastructure.security.JwtConfig;
import com.matheuslima.logistics.shared.infrastructure.security.ProblemDetailAccessDeniedHandler;
import com.matheuslima.logistics.shared.infrastructure.security.ProblemDetailAuthenticationEntryPoint;
import com.matheuslima.logistics.shared.infrastructure.security.SecurityConfig;
import com.matheuslima.logistics.shared.infrastructure.time.ClockConfig;
import com.matheuslima.logistics.shared.infrastructure.web.ProblemDetailFactory;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotação composta para testar <b>adaptadores web</b> (controllers) com a segurança real.
 *
 * <h2>Por que uma anotação própria?</h2>
 * {@code @WebMvcTest} sobe só a camada web: controllers, {@code @RestControllerAdvice}, filtros e
 * {@code WebMvcConfigurer}. Classes {@code @Configuration} e {@code @Component} comuns ficam de
 * fora. Para testar com a mesma segurança e o mesmo formato de erro de produção, precisamos
 * importar algumas peças. Em vez de repetir a lista em todo teste, ela fica aqui, num lugar só.
 *
 * <p>Os casos de uso são substituídos por mocks ({@code @MockitoBean}) em cada teste: o objetivo
 * é verificar o <b>contrato HTTP</b> (rotas, status, JSON, validação, autorização), e não as
 * regras de negócio, que já têm testes próprios.
 *
 * <p>{@code @AliasFor} permite escrever {@code @WebAdapterTest(ShipmentController.class)} e repassar
 * o valor para {@code @WebMvcTest(controllers = ...)}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import({SecurityConfig.class, JwtConfig.class, ProblemDetailFactory.class, ClockConfig.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
@TestPropertySource(properties = "app.security.jwt.secret=" + TestJwt.SECRET)
public @interface WebAdapterTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
