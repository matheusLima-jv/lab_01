package com.matheuslima.logistics;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * <b>Regras de arquitetura executáveis</b> (ArchUnit).
 *
 * <h2>Por que testar a arquitetura?</h2>
 * Diagramas e documentos descrevem a arquitetura desejada; o código descreve a real. Com o
 * tempo, sob pressão de prazo, alguém importa uma classe do Spring no domínio "só desta vez", e
 * a arquitetura apodrece sem que ninguém perceba. O ArchUnit analisa o bytecode compilado e falha
 * o build quando uma regra é violada, com uma mensagem dizendo exatamente qual classe quebrou qual
 * regra. A arquitetura vira um contrato verificado a cada {@code mvn test}, não só uma intenção.
 *
 * <p>São testes rápidos (análise estática, sem Spring), por isso rodam com os testes unitários.
 */
@DisplayName("Regras de arquitetura")
class ArchitectureTest {

    private static final String ROOT = "com.matheuslima.logistics";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Nested
    @DisplayName("hexagonal / Clean Architecture")
    class Hexagonal {

        @Test
        @DisplayName("as dependências entre camadas apontam sempre para dentro")
        void layersPointInwards() {
            ArchRule rule = layeredArchitecture().consideringOnlyDependenciesInLayers()
                    .layer("Domain").definedBy("..domain..")
                    .layer("Application").definedBy("..application..")
                    .layer("Adapters").definedBy("..adapter..")
                    .layer("Infrastructure").definedBy(ROOT + ".shared.infrastructure..")
                    .layer("Config").definedBy("..config..")
                    .whereLayer("Config").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Config")
                    .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Adapters", "Config")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Infrastructure", "Config")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Infrastructure",
                            "Config");

            rule.check(productionClasses);
        }

        @Test
        @DisplayName("o domínio é Java puro: sem Spring, JPA, Jackson ou Hibernate")
        void domainIsFrameworkFree() {
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta..", "org.hibernate..", "tools.jackson..",
                            "com.fasterxml..", "io.github.resilience4j..")
                    .because("o domínio deve poder ser testado e evoluído sem nenhum framework")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("a camada de aplicação não depende de framework (nem de anotações do Spring)")
        void applicationIsFrameworkFree() {
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta.persistence..", "org.hibernate..", "tools.jackson..",
                            "io.github.resilience4j..")
                    .because("casos de uso são registrados como beans pela configuração, e não por anotações")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("adaptadores de entrada não chamam adaptadores de saída (passam pelos casos de uso)")
        void inboundAdaptersDoNotUseOutboundAdapters() {
            noClasses().that().resideInAPackage("..adapter.in..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
                    .because("um controller que acessa o repositório pula as regras do caso de uso")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("adaptadores de saída não dependem de adaptadores de entrada")
        void outboundAdaptersDoNotUseInboundAdapters() {
            noClasses().that().resideInAPackage("..adapter.out..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter.in..")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("o domínio não conhece portas nem casos de uso")
        void domainDoesNotKnowApplication() {
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..application..")
                    .check(productionClasses);
        }
    }

    @Nested
    @DisplayName("DDD / bounded contexts")
    class BoundedContexts {

        @Test
        @DisplayName("identidade e envios não dependem um do outro (só se referenciam por ID)")
        void contextsAreIndependent() {
            noClasses().that().resideInAPackage(ROOT + ".identity..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".shipping..")
                    .check(productionClasses);
            noClasses().that().resideInAPackage(ROOT + ".shipping..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".identity..")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("o shared kernel não depende de nenhum contexto")
        void sharedKernelIsIndependent() {
            noClasses().that().resideInAPackage(ROOT + ".shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".identity..", ROOT + ".shipping..")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("não há ciclos de dependência entre os pacotes de cada contexto")
        void noCycles() {
            slices().matching(ROOT + ".(*).(*)..").should().beFreeOfCycles().check(productionClasses);
        }
    }

    @Nested
    @DisplayName("convenções")
    class Conventions {

        @Test
        @DisplayName("implementações de casos de uso ficam em application.service e terminam com 'Service'")
        void useCaseImplementations() {
            classes().that().implement(com.tngtech.archunit.base.DescribedPredicate.describe(
                            "a use case port", javaClass -> javaClass.getSimpleName().endsWith("UseCase")
                                    && javaClass.getPackageName().endsWith("application.port.in")))
                    .should().resideInAPackage("..application.service..")
                    .andShould().haveSimpleNameEndingWith("Service")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("controllers REST ficam em adapter.in.web")
        void controllersLiveInWebAdapters() {
            classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().resideInAPackage("..adapter.in.web..")
                    .andShould().haveSimpleNameEndingWith("Controller")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("entidades JPA ficam nos adaptadores de persistência ou na infraestrutura")
        void jpaEntitiesStayOutOfTheCore() {
            classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAnyPackage("..adapter.out.persistence..", ROOT + ".shared.infrastructure..")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("injeção por construtor (sem @Autowired em campo), sem System.out e sem java.util.logging")
        void generalCodingRules() {
            NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(productionClasses);
            NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(productionClasses);
            NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(productionClasses);
        }
    }
}
