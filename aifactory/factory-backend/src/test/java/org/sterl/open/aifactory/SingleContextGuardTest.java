package org.sterl.open.aifactory;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * The one-context rule of the build as an executable rule: the Spring context starts exactly once, so
 * nothing may give a test class a context of its own ({@code aifactory/AGENTS.md}, measured history in
 * {@code aifactory/AGENTS-DEV.md}).
 *
 * <p>Discipline is not a guard. The cost of a split is invisible in a green run — 23 per-class
 * {@code @DynamicPropertySource} methods once cost 23 contexts and 77 s of wall clock — so the shape of
 * the test tree is checked here instead of reviewed by eye. ArchUnit reads bytecode, which is what
 * Spring's context cache keys on, and the rules run without a context of their own.</p>
 *
 * <p>{@link FactorySpringTest} is the single owner of the annotation, the {@code FakeGitConfig} import
 * and the dynamic home; {@code assertSameContext} in that base is the runtime half of the same rule.</p>
 */
class SingleContextGuardTest {

    private static final String BASE_PACKAGE = "org.sterl.open.aifactory";
    private static final String BASE = BASE_PACKAGE + ".FactorySpringTest";
    private static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";
    private static final String SPRING_APPLICATION_BUILDER =
            "org.springframework.boot.builder.SpringApplicationBuilder";

    /**
     * Everything that changes the context cache key or replaces the context — a class or a member
     * declaration of one gives the declaring class its own application under test. Matched by name:
     * these annotations live in three different jars and the rule must not depend on which one is
     * present, only on what the source says.
     */
    private static final Set<String> CONTEXT_SPLITTING = Set.of(
            "DynamicPropertySource", // a per-class method is a per-class cache key
            "DirtiesContext", // closes the shared context in the middle of the build
            "TestPropertySource", "ActiveProfiles", "ContextConfiguration", "ContextHierarchy",
            "MockitoBean", "MockitoSpyBean", // a mock bean is a bean-definition change is a new context
            "WebMvcTest", "WebFluxTest", "DataJpaTest", "JsonTest"); // Boot slices: a context of their own

    /**
     * The only classes allowed to boot an application outside the shared context: a restart is proven
     * by a second application over the same home, never by {@code @DirtiesContext}
     * ({@code aifactory/AGENTS-DEV.md}). Such a boot never enters the context cache, so it is not a
     * context of the measurement — but it is a second server, so it stays countable here.
     */
    private static final List<String> RESTART_PROOFS = List.of(
            "ChatRestartTest", // the fact itself: a turn never survives a restart
            "DelegatedAnswerRestartTest", // a delegated answer must not survive it either
            "ProjectStatusTest"); // UC-WP-47 — since Inc 3 its own project, so its own restart

    private final JavaClasses imported = new ClassFileImporter().importPackages(BASE_PACKAGE);

    @Test
    void onlyTheBaseDeclaresTheSpringBootTest() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass test : springRelevantTests()) {
            if (test.getName().equals(BASE)) {
                continue;
            }
            for (JavaAnnotation<?> annotation : test.getAnnotations()) {
                if (annotation.getRawType().getName().equals(SPRING_BOOT_TEST)) {
                    offenders.add(test.getSimpleName()
                            + " deklariert @SpringBootTest — erbt stattdessen FactorySpringTest");
                }
            }
        }

        assertThat(offenders).as("a @SpringBootTest of its own is a second context of its own")
                .isEmpty();
    }

    @Test
    void noTestDeclaresItsOwnContextConfiguration() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass test : springRelevantTests()) {
            collect(test, test.getAnnotations(), test.getSimpleName(), offenders);
            test.getMethods().forEach(method -> collect(test, method.getAnnotations(),
                    test.getSimpleName() + "#" + method.getName(), offenders));
            test.getFields().forEach(field -> collect(test, field.getAnnotations(),
                    test.getSimpleName() + "." + field.getName(), offenders));
        }

        assertThat(offenders).as("only the base may touch the context configuration — see FactorySpringTest")
                .isEmpty();
    }

    @Test
    void theSharedContextIsTheOneAndTheSameEverywhere() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass test : springRelevantTests()) {
            for (JavaAnnotation<?> annotation : test.getAnnotations()) {
                if (!annotation.getRawType().getName().equals(SPRING_BOOT_TEST)) {
                    continue;
                }
                if (hasEntries(annotation, "properties")) {
                    offenders.add(test.getSimpleName() + " trägt @SpringBootTest(properties = …)");
                }
                if (test.getName().equals(BASE)) {
                    if (!rendersRealServer(annotation)) {
                        offenders.add("FactorySpringTest läuft nicht auf RANDOM_PORT — ohne echten "
                                + "Server braucht jede Klasse ihren eigenen Kontext");
                    }
                } else if (!rendersRealServer(annotation)) {
                    offenders.add(test.getSimpleName() + " deklariert @SpringBootTest mit webEnvironment = "
                            + property(annotation, "webEnvironment").map(String::valueOf)
                                    .orElse("nicht angegeben")
                            + " statt RANDOM_PORT wie der geteilte Kontext");
                }
            }
        }

        assertThat(offenders).as("properties and webEnvironment are part of the cache key")
                .isEmpty();
    }

    /**
     * Both directions at once: a new second boot is red, and an entry whose boot is gone is red too —
     * the list may never grow stale, or it stops being an exception list.
     */
    @Test
    void theRestartProofsStayTheOnlySecondBoots() {
        Set<String> booting = imported.stream()
                .filter(SingleContextGuardTest::bootsItsOwnApplication)
                .map(JavaClass::getSimpleName)
                .collect(toCollection(TreeSet::new));

        assertThat(booting).as("classes booting an application outside the shared context")
                .containsExactlyElementsOf(RESTART_PROOFS);
    }

    // ------------------------------------------------------------------ the scan

    /** The classes of this package that take part in the Spring test tree. */
    private List<JavaClass> springRelevantTests() {
        return imported.stream().filter(SingleContextGuardTest::isTestName).toList();
    }

    private static boolean isTestName(JavaClass javaClass) {
        String name = javaClass.getSimpleName();
        return name.endsWith("Test") || name.endsWith("Tests");
    }

    private static void collect(JavaClass owner, Set<? extends JavaAnnotation<?>> annotations, String where,
            List<String> offenders) {
        if (owner.getName().equals(BASE)) {
            return; // the one owner of the shared configuration
        }
        for (JavaAnnotation<?> annotation : annotations) {
            String name = annotation.getRawType().getSimpleName();
            if (CONTEXT_SPLITTING.contains(name)) {
                offenders.add(where + " deklariert @" + name);
            }
        }
    }

    private static boolean bootsItsOwnApplication(JavaClass javaClass) {
        return javaClass.getDirectDependenciesFromSelf().stream()
                .anyMatch(dependency -> dependency.getTargetClass().getName().equals(SPRING_APPLICATION_BUILDER));
    }

    private static boolean hasEntries(JavaAnnotation<?> annotation, String key) {
        return property(annotation, key)
                .map(value -> value instanceof Iterable<?> entries ? entries.iterator().hasNext()
                        : !String.valueOf(value).isBlank())
                .orElse(false);
    }

    private static boolean rendersRealServer(JavaAnnotation<?> annotation) {
        return property(annotation, "webEnvironment")
                .map(value -> String.valueOf(value).contains("RANDOM_PORT"))
                .orElse(false);
    }

    private static Optional<Object> property(JavaAnnotation<?> annotation, String key) {
        return annotation.tryGetExplicitlyDeclaredProperty(key);
    }
}
