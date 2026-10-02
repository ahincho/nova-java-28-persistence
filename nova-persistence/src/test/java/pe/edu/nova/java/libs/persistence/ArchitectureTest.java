package pe.edu.nova.java.libs.persistence;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** El núcleo es una librería pura (ADR-015): no conoce ningún framework, ni el de JPA ni una librería de JSON. */
class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("pe.edu.nova.java.libs.persistence");

    @Test
    void theCoreDependsOnNoFramework() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "io.quarkus..",
                        "jakarta..",
                        "org.hibernate..",
                        "com.fasterxml..",
                        "tools.jackson..",
                        "org.slf4j..")
                .check(classes);
    }
}
