package org.sterl.open.aifactory;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * PR-4 / AF-ADR-001 as an executable rule: the factory talks to the Core through plain disk paths
 * only — no Eclipse runtime, no plugin composition root.
 *
 * <p>ArchUnit is not declared by this module on purpose: {@code spring-modulith-core} already pulls
 * {@code com.tngtech.archunit:archunit} in as a non-optional compile dependency, so the rules run as
 * ordinary {@link com.tngtech.archunit.lang.ArchRule#check} calls in plain {@code @Test} methods.
 * {@code @ArchTest} would need the {@code archunit-junit5} artifact, which is not available
 * offline — and neither is a download an option here.</p>
 */
class ArchitectureGuardTest {

    static final String BASE_PACKAGE = "org.sterl.open.aifactory";

    /** Plugin types that only work inside the Eclipse plugin — forbidden in factory production code. */
    private static final List<String> PLUGIN_ONLY_SIMPLE_NAMES = List.of(
            "PeonAiService", "SharedToolsComponent", "AgentContextComponent", "BuildPoAgentComponent",
            "AskUserTool", "WorkspaceMemoryTool", "PlanTool", "PlanReadTool",
            "StaticContextItem", "AgentsMdContextItem");

    /**
     * Imports production classes only — these rules guard the shipped code; a test class may use test
     * frameworks without tripping them. An empty import would fail the rule instead of greening it.
     */
    static JavaClasses productionClasses() {
        return new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE_PACKAGE);
    }

    @Test
    void factoryNeverDependsOnEclipse() {
        noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAnyPackage("org.eclipse..")
                .because("the factory shares the Core through plain disk paths (PR-4, AF-ADR-001)")
                .check(productionClasses());
    }

    @Test
    void pluginOnlyTypesAreNeverUsed() {
        noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
                .should().dependOnClassesThat(pluginOnlyType())
                .because("plugin components need an Eclipse runtime the factory never starts")
                .check(productionClasses());
    }

    /**
     * {@code org.sterl.llmpeon.*} from {@code llmpeon-core} is the <em>allowed</em> dependency — from
     * Inc 3 the {@code agent} package may use it. What is forbidden are the types of the Eclipse
     * plugin composition root, which cannot work outside an Eclipse runtime.
     */
    private static DescribedPredicate<JavaClass> pluginOnlyType() {
        List<DescribedPredicate<? super JavaClass>> forbidden = new ArrayList<>();
        forbidden.add(simpleNameStartingWith("Eclipse"));
        PLUGIN_ONLY_SIMPLE_NAMES.forEach(name -> forbidden.add(simpleName(name)));
        return DescribedPredicate.or(forbidden);
    }
}
