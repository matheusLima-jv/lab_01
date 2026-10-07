package com.matheuslima.logistics.shared;

import com.matheuslima.logistics.shared.application.ExternalServiceUnavailableException;
import com.matheuslima.logistics.shared.application.ForbiddenOperationException;
import com.matheuslima.logistics.shared.application.PageQuery;
import com.matheuslima.logistics.shared.application.PageResult;
import com.matheuslima.logistics.shared.application.Requester;
import com.matheuslima.logistics.shared.domain.DomainValidationException;
import com.matheuslima.logistics.shared.domain.ErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes dos tipos compartilhados (shared kernel) usados por todos os contextos.
 */
@DisplayName("Shared kernel")
class SharedKernelTest {

    @Nested
    @DisplayName("PageQuery")
    class PageQueryTests {

        @Test
        @DisplayName("calcula o offset e oferece a primeira página padrão")
        void offset() {
            assertThat(new PageQuery(3, 20).offset()).isEqualTo(60);
            assertThat(PageQuery.firstPage()).isEqualTo(new PageQuery(0, PageQuery.DEFAULT_SIZE));
        }

        @ParameterizedTest(name = "page={0}, size={1} é inválido")
        @CsvSource({"-1, 10", "0, 0", "0, 101"})
        void rejectsInvalidValues(int page, int size) {
            assertThatThrownBy(() -> new PageQuery(page, size)).isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("PageResult")
    class PageResultTests {

        @ParameterizedTest(name = "{0} itens, páginas de {1} = {2} páginas")
        @CsvSource({"0, 10, 0", "1, 10, 1", "10, 10, 1", "11, 10, 2", "95, 20, 5"})
        void totalPages(long totalItems, int size, int expectedPages) {
            assertThat(new PageResult<>(List.of(), 0, size, totalItems).totalPages()).isEqualTo(expectedPages);
        }

        @Test
        @DisplayName("map converte itens preservando metadados")
        void mapKeepsMetadata() {
            PageResult<Integer> numbers = new PageResult<>(List.of(1, 2), 1, 2, 5);

            PageResult<String> mapped = numbers.map(n -> "#" + n);

            assertThat(mapped.items()).containsExactly("#1", "#2");
            assertThat(mapped.page()).isEqualTo(1);
            assertThat(mapped.totalItems()).isEqualTo(5);
            assertThat(mapped.totalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("faz cópia defensiva: alterar a lista original não altera a página")
        void defensiveCopy() {
            List<String> source = new ArrayList<>(List.of("a"));
            PageResult<String> page = new PageResult<>(source, 0, 10, 1);

            source.add("b");

            assertThat(page.items()).containsExactly("a");
            assertThatThrownBy(() -> page.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Requester")
    class RequesterTests {

        private final UUID owner = UUID.randomUUID();

        @Test
        @DisplayName("cliente acessa apenas os próprios recursos")
        void customerAccessesOnlyOwnResources() {
            assertThat(Requester.customer(owner).canAccessResourceOwnedBy(owner)).isTrue();
            assertThat(Requester.customer(UUID.randomUUID()).canAccessResourceOwnedBy(owner)).isFalse();
        }

        @Test
        @DisplayName("operador acessa qualquer recurso")
        void operatorAccessesEverything() {
            assertThat(Requester.operatorUser(UUID.randomUUID()).canAccessResourceOwnedBy(owner)).isTrue();
        }
    }

    @Test
    @DisplayName("exceções de aplicação carregam categoria, código e causa")
    void applicationExceptions() {
        var cause = new IOException("connection reset");
        var unavailable = new ExternalServiceUnavailableException("ViaCEP", cause);
        var forbidden = new ForbiddenOperationException("Somente operadores");

        assertThat(unavailable.category()).isEqualTo(ErrorCategory.UNAVAILABLE);
        assertThat(unavailable.code()).isEqualTo("EXTERNAL_SERVICE_UNAVAILABLE");
        assertThat(unavailable.serviceName()).isEqualTo("ViaCEP");
        assertThat(unavailable.getCause()).isSameAs(cause);
        assertThat(unavailable.getMessage()).contains("ViaCEP");

        assertThat(forbidden.category()).isEqualTo(ErrorCategory.FORBIDDEN);
        assertThat(forbidden.code()).isEqualTo("OPERATION_NOT_ALLOWED");
    }
}
