package org.sterl.open.aifactory;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

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
 * <p>Two things decide whether this guard can fail open: <b>what it scans</b> and <b>what it reads</b>.
 * It scans the test class path and never a name schema — an intermediate class without a {@code Test}
 * suffix takes part in the Spring tree exactly like one with it, and Spring inherits its annotations.
 * And it reads a declaration together with its meta-annotations: a home-grown {@code @FactoryBootTest}
 * carrying {@code @SpringBootTest} is the same second context as the annotation itself, so every rule
 * resolves a declaration through the annotation types of this tree ({@link #carriedByAnnotation()}).</p>
 *
 * <p>{@link FactorySpringTest} is the single owner of the annotation, the {@code FakeGitConfig} import
 * and the dynamic home; {@code assertOneSharedContext} in that base is the runtime half of the same
 * rule.</p>
 */
class SingleContextGuardTest {

    private static final String BASE_PACKAGE = "org.sterl.open.aifactory";
    private static final String BASE = BASE_PACKAGE + ".FactorySpringTest";
    private static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";
    private static final String SPRING_BOOT_TEST_NAME = "SpringBootTest";
    private static final String SPRING_APPLICATION_BUILDER =
            "org.springframework.boot.builder.SpringApplicationBuilder";

    /** Test classes have their own output dir; that is the scope of the scan, not a name pattern. */
    private static final String TEST_CLASSES = "test-classes";

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

    /** What a declaration is measured against: the splitting annotations plus {@code @SpringBootTest}. */
    private static final Set<String> SPLITTING = Stream
            .concat(CONTEXT_SPLITTING.stream(), Stream.of(SPRING_BOOT_TEST_NAME))
            .collect(toUnmodifiableSet());

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

    /**
     * Imported once for the whole class: JUnit builds an instance per test method, and four imports of
     * the test tree would cost more than all four rules together.
     */
    private static final JavaClasses imported = new ClassFileImporter().importPackages(BASE_PACKAGE);

    /** Annotation types of this tree by name — the walk into a meta-annotation needs them. */
    private static final Map<String, JavaClass> annotationTypes = annotationTypesByName();

    /** Our annotation types to the splitting annotations they carry, directly or through another one. */
    private static final Map<String, Set<String>> carriedByAnnotation = carriedByAnnotation();

    @Test
    void onlyTheBaseDeclaresTheSpringBootTest() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass test : testTreeClasses()) {
            if (test.getName().equals(BASE)) {
                continue;
            }
            for (JavaAnnotation<?> annotation : test.getAnnotations()) {
                if (carries(annotation, SPRING_BOOT_TEST_NAME)) {
                    offenders.add(test.getName() + " erklärt @" + annotation.getRawType().getSimpleName()
                            + carrying(annotation) + " — erbt stattdessen FactorySpringTest");
                }
            }
        }

        assertThat(offenders).as("a @SpringBootTest of its own is a second context of its own")
                .isEmpty();
    }

    @Test
    void noTestDeclaresItsOwnContextConfiguration() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass test : testTreeClasses()) {
            collect(test, test.getAnnotations(), test.getName(), offenders);
            test.getMethods().forEach(method -> collect(test, method.getAnnotations(),
                    test.getName() + "#" + method.getName(), offenders));
            test.getFields().forEach(field -> collect(test, field.getAnnotations(),
                    test.getName() + "." + field.getName(), offenders));
        }

        assertThat(offenders).as("only the base may touch the context configuration — see FactorySpringTest")
                .isEmpty();
    }

    @Test
    void theSharedContextIsTheOneAndTheSameEverywhere() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass test : testTreeClasses()) {
            for (SpringBootTest declaration : springBootTests(test, new HashSet<>(), "")) {
                if (hasEntries(declaration.annotation(), "properties")) {
                    offenders.add(test.getName() + " trägt " + declaration.through()
                            + "@SpringBootTest(properties = …)");
                }
                if (test.getName().equals(BASE)) {
                    if (!rendersRealServer(declaration.annotation())) {
                        offenders.add("FactorySpringTest läuft nicht auf RANDOM_PORT — ohne echten "
                                + "Server braucht jede Klasse ihren eigenen Kontext");
                    }
                } else if (!rendersRealServer(declaration.annotation())) {
                    offenders.add(test.getName() + " deklariert " + declaration.through()
                            + "@SpringBootTest mit webEnvironment = "
                            + property(declaration.annotation(), "webEnvironment").map(String::valueOf)
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

    /**
     * Every class of the test class path under this package. A name filter would be the gap: what
     * Spring inherits from is a base class, an intermediate helper or an annotation type, and none of
     * those has to be called {@code *Test}.
     */
    private static List<JavaClass> testTreeClasses() {
        return imported.stream().filter(SingleContextGuardTest::onTestClassPath).toList();
    }

    /** A class of unknown origin stays in scope — dropping it would be the gap again. */
    private static boolean onTestClassPath(JavaClass javaClass) {
        return javaClass.getSource().map(source -> source.getUri().toString().contains(TEST_CLASSES))
                .orElse(true);
    }

    private static Map<String, JavaClass> annotationTypesByName() {
        Map<String, JavaClass> types = new HashMap<>();
        imported.stream().filter(JavaClass::isAnnotation)
                .forEach(type -> types.put(type.getName(), type));
        return types;
    }

    /**
     * The fixpoint over the annotation types of this tree: an annotation carries a splitting annotation
     * if it declares one, or declares an annotation that carries one. Names, not types — the splitting
     * annotations live in three jars and this must not depend on them being imported.
     */
    private static Map<String, Set<String>> carriedByAnnotation() {
        Map<String, Set<String>> declared = declaredAnnotationNames();
        Map<String, Set<String>> carried = new HashMap<>();
        boolean grown = true;
        while (grown) {
            grown = false;
            for (Map.Entry<String, Set<String>> type : declared.entrySet()) {
                Set<String> found = new HashSet<>(carried.getOrDefault(type.getKey(), Set.of()));
                type.getValue().stream().filter(SPLITTING::contains).forEach(found::add);
                declared.entrySet().stream()
                        .filter(other -> type.getValue().contains(simpleName(other.getKey())))
                        .forEach(other -> found.addAll(carried.getOrDefault(other.getKey(), Set.of())));
                if (!found.isEmpty() && !found.equals(carried.getOrDefault(type.getKey(), Set.of()))) {
                    carried.put(type.getKey(), found);
                    grown = true;
                }
            }
        }
        return carried;
    }

    /** Annotation type to the simple names of the annotations it declares. */
    private static Map<String, Set<String>> declaredAnnotationNames() {
        Map<String, Set<String>> declared = new LinkedHashMap<>();
        imported.stream().filter(JavaClass::isAnnotation).forEach(type -> declared.put(type.getName(),
                type.getAnnotations().stream()
                        .map(annotation -> annotation.getRawType().getSimpleName())
                        .collect(toCollection(LinkedHashSet::new))));
        return declared;
    }

    /** True if this annotation is the splitting one, or an annotation of this tree carrying it. */
    private static boolean carries(JavaAnnotation<?> annotation, String splitting) {
        return annotation.getRawType().getSimpleName().equals(splitting)
                || carriedByAnnotation.getOrDefault(annotation.getRawType().getName(), Set.of())
                        .contains(splitting);
    }

    /** Names the way through: {@code " (Meta-Annotation von @SpringBootTest)"} for a carrier. */
    private static String carrying(JavaAnnotation<?> annotation) {
        String name = annotation.getRawType().getSimpleName();
        if (SPLITTING.contains(name)) {
            return "";
        }
        return " (Meta-Annotation von " + carriedByAnnotation
                .getOrDefault(annotation.getRawType().getName(), Set.of()).stream().sorted()
                .map(carried -> "@" + carried).reduce((left, right) -> left + ", " + right).orElse("") + ")";
    }

    /** Where {@code @SpringBootTest} is in effect for a class: its own declaration, or a meta one. */
    private record SpringBootTest(JavaAnnotation<?> annotation, String through) {
    }

    private static List<SpringBootTest> springBootTests(JavaClass owner, Set<String> visited, String through) {
        List<SpringBootTest> found = new ArrayList<>();
        if (!visited.add(owner.getName())) {
            return found;
        }
        for (JavaAnnotation<?> annotation : owner.getAnnotations()) {
            String name = annotation.getRawType().getName();
            if (name.equals(SPRING_BOOT_TEST)) {
                found.add(new SpringBootTest(annotation, through));
            } else if (carries(annotation, SPRING_BOOT_TEST_NAME) && annotationTypes.containsKey(name)) {
                // the declaration of the meta-annotation is the one Spring reads for this class
                found.addAll(springBootTests(annotationTypes.get(name), visited,
                        through + "@" + annotation.getRawType().getSimpleName() + " → "));
            }
        }
        visited.remove(owner.getName());
        return found;
    }

    private static void collect(JavaClass owner, Set<? extends JavaAnnotation<?>> annotations, String where,
            List<String> offenders) {
        if (owner.getName().equals(BASE)) {
            return; // the one owner of the shared configuration
        }
        for (JavaAnnotation<?> annotation : annotations) {
            for (String splitting : SPLITTING) {
                if (carries(annotation, splitting)) {
                    offenders.add(where + " deklariert @" + annotation.getRawType().getSimpleName()
                            + carrying(annotation));
                    break; // one offender per declaration is enough to read
                }
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

    private static String simpleName(String name) {
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? name : name.substring(lastDot + 1);
    }
}
