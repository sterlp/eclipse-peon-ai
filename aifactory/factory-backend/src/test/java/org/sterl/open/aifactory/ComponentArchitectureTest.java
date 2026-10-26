package org.sterl.open.aifactory;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.tngtech.archunit.core.domain.JavaClasses;

import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfigLoader;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.poagent.AiPoAgent;

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

    /**
     * Reading the factory's config is the {@code config} component's alone: {@code LlmConfigLoader}
     * answers an unpinned {@code LlmConfig}, and {@code FileLlmConfigStore} is the file itself — so a
     * second user of either builds a model around the two values the factory decides for itself
     * (config dir UC-WP-36, disk tools UC-WP-37). Increment 3's runtime therefore takes the config
     * from {@code FactoryConfigService}, it does not load one.
     */
    @Test
    void onlyConfigReadsTheFactorysOwnConfig() {
        JavaClasses productionClasses = ArchitectureGuardTest.productionClasses();

        noClasses().that().resideOutsideOfPackage(ArchitectureGuardTest.BASE_PACKAGE + ".config..")
                .should().dependOnClassesThat(belongToAnyOf(LlmConfigLoader.class, FileLlmConfigStore.class))
                .because("only config loads the factory's own config — anywhere else the pinning is bypassed")
                .check(productionClasses);
    }

    /**
     * A turn belongs to a project, so an agent instance belongs to exactly one component: {@code chat}.
     * Anywhere else it would be a second runtime of its own — a second Jon per project, a second memory
     * and a second serialization point, which is what R-WP-10 and R-WP-15 rule out. The guard names the
     * agent types and the model wrapper, because the wrapper is what a hand-built agent starts with.
     */
    @Test
    void onlyChatHoldsAgentInstances() {
        JavaClasses productionClasses = ArchitectureGuardTest.productionClasses();

        noClasses().that().resideOutsideOfPackage(ArchitectureGuardTest.BASE_PACKAGE + ".chat..")
                .should().dependOnClassesThat().areAssignableTo(AiPoAgent.class)
                .orShould().dependOnClassesThat().areAssignableTo(AiPlanAgent.class)
                .orShould().dependOnClassesThat().areAssignableTo(AiDevAgent.class)
                .orShould().dependOnClassesThat().areAssignableTo(AiReviewAgent.class)
                .orShould().dependOnClassesThat().areAssignableTo(ConfiguredChatModel.class)
                .because("only chat builds and owns the agents of a project — anyone else holding one "
                        + "is a second Jon on the same memory")
                .check(productionClasses);
    }
}
