package com.matheuslima.logistics.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Atribui um <b>correlation ID</b> a cada requisição e o coloca nos logs e na resposta.
 *
 * <h2>Para que serve?</h2>
 * Com várias requisições simultâneas, as linhas de log se misturam. Com o ID em toda linha
 * (padrão de log no {@code application.yml}), dá para filtrar exatamente o que aconteceu em uma
 * requisição. O ID volta no header {@code X-Correlation-Id} e no corpo dos erros: o cliente
 * informa o ID ao suporte, e o suporte encontra os logs na hora.
 *
 * <p>Se quem chama já envia um ID (outro serviço, um API Gateway), ele é reaproveitado e a
 * requisição pode ser rastreada através de vários sistemas.
 *
 * <h2>Por que validar o ID recebido?</h2>
 * O valor vem do cliente e vai parar nos logs. Sem validação, alguém poderia enviar quebras de
 * linha e injetar linhas falsas no log (<i>log injection</i>, CWE-117), ou enviar um texto enorme.
 * Aceitamos só caracteres seguros e tamanho limitado; caso contrário, geramos um novo ID.
 *
 * <h2>Por que ordem máxima de precedência?</h2>
 * Para rodar antes do filtro do Spring Security: assim até os erros 401/403 têm correlation ID.
 *
 * <p><b>Evolução:</b> com distributed tracing (Micrometer Tracing + OpenTelemetry), os IDs de
 * trace/span W3C ({@code traceparent}) assumem esse papel, inclusive atravessando o RabbitMQ.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String received = request.getHeader(HEADER);
        String correlationId = received != null && SAFE_ID.matcher(received).matches()
                ? received
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // MDC é ThreadLocal: sem limpar, o ID "vazaria" para a próxima requisição atendida
            // pela mesma thread (relevante com pools de threads de plataforma).
            MDC.remove(MDC_KEY);
        }
    }
}
