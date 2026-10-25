package org.sterl.open.aifactory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.tngtech.archunit.core.domain.JavaClasses;

/**
 * The component rules of {@code aifactory/AGENTS.md} (skill: component-architecture) as executable
 * guards, so a violation is a build failure and not a review finding.
 */
class ComponentArchitectureTest {

    /**
     * Modulith parses the application's modules and verifies them: no cycles between modules, no use
     * of another module's internal types. Runs on the classpath — no Spring context, no download
     * ({@code spring-modulith-starter-test} is already a declared test dependency).
     */
    @Test
    void applicationModulesVerify() {
        ApplicationModules.of(AifactoryApplication.class).verify();
    }

    /**
     * "shared kennt keine Komponente" ({@code aifactory/docs/aifactory-architektur.md}) as an
     * allow-list instead of a deny-list: everything {@code shared} may depend on is the JDK and the
     * framework. Red for an advice in {@code shared} that handles a component's own type — and just
     * as red for {@code shared} reaching for another component's framework, which a deny-list of the
     * one known offender would let through.
     */
    @Test
    void sharedKnowsNoComponent() {
        JavaClasses productionClasses = ArchitectureGuardTest.productionClasses();

        noClasses().that().resideInAPackage(ArchitectureGuardTest.BASE_PACKAGE + ".shared..")
                .should().dependOnClassesThat()
                .resideOutsideOfPackages("java..", "javax..", "jdk..", "org.springframework..",
                        "com.fasterxml.jackson..", ArchitectureGuardTest.BASE_PACKAGE + ".shared..")
                .because("shared is importable from every component but may know no component itself")
                .check(productionClasses);
    }
}
