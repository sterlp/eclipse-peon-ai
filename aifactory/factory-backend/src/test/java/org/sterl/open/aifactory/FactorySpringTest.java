package org.sterl.open.aifactory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.project.ProjectService;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The base of every Spring test of the factory — it is what keeps
 * {@code aifactory/AGENTS.md} ("the context starts at most once per build") true.
 *
 * <p>The cache key of a context is built from its configuration, and a per-class
 * {@code @DynamicPropertySource} is part of that key: two classes declaring their own method are never
 * equal, so each got its own server. Declaring the annotation, the {@link FakeGitConfig} import and the
 * one dynamic property <em>here</em> makes the key identical for every subclass — Spring finds the
 * inherited static method, and the {@code Method} object is the same for all of them.</p>
 *
 * <p>One context means one home for the whole build, so the home is a fixed path below
 * {@code target/} and not a {@code @TempDir}: on macOS {@code java.io.tmpdir} is one of the two allowed
 * write roots, and a home inside it can never prove that {@code ..} escapes
 * ({@code aifactory/AGENTS-DEV.md}). Every test method therefore starts on an empty factory — see
 * {@link #resetSharedState()}.</p>
 *
 * <p>A subclass adds nothing of the above; it adds its own provider double and its own client.</p>
 */
@SpringBootTest(classes = AifactoryApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
public abstract class FactorySpringTest {

    /** The one home of the build — a project home never lives inside {@code java.io.tmpdir}. */
    public static final Path HOME = Path.of("target", "test-home").toAbsolutePath().normalize();

    /** First context seen by any subclass; a second instance is the rule breaking, so it is kept. */
    private static final AtomicReference<ApplicationContext> FIRST_CONTEXT = new AtomicReference<>();

    /** Shared application — a subclass that asserts "nothing was restarted" asserts on this one. */
    @Autowired
    protected ApplicationContext context;

    @Autowired
    protected Environment environment;

    @Autowired
    protected ProjectService projects;

    @Autowired
    protected FakeGitConfig fakeGit;

    @DynamicPropertySource
    static void sharedHome(DynamicPropertyRegistry registry) {
        registry.add(FactoryHome.HOME_PROPERTY, () -> HOME.toString());
    }

    /**
     * Cheap and immediate proof of the rule itself: the moment a second context is built — by a new
     * {@code @DynamicPropertySource}, a {@code @MockitoBean}, a {@code @DirtiesContext} or a different
     * {@code webEnvironment} — this fails on the first test of the second class, whatever the order is.
     */
    @BeforeEach
    void assertOneSharedContext() {
        ApplicationContext seen = FIRST_CONTEXT.updateAndGet(previous -> previous == null ? context : previous);
        assertThat(seen).as("every Spring test shares the one context of the build")
                .isNotNull()
                .isSameAs(context);
        assertThat(environment.getProperty(FactoryHome.HOME_PROPERTY))
                .as("and it runs on the shared home, not on a home of its own")
                .isEqualTo(HOME.toString());
    }

    /**
     * Empty factory per test method. Removal goes through {@link ProjectService#remove(String)} and not
     * through the wipe below: remove is what publishes {@code ProjectRemoved}, which is what drops the
     * project's runtime and its streams — and it needs the checkout, so it runs while the tree is there.
     * Wiping the home alone would leave a runtime of the previous test registered under the same id, and
     * the id comes from the git url, so the next test would inherit it.
     */
    @BeforeEach
    void resetSharedState() throws IOException {
        for (Project leftover : projects.projects()) {
            projects.remove(leftover.id());
        }
        deleteTree(HOME);
        Files.createDirectories(HOME);
        fakeGit.failWith = null;
        fakeGit.cloneTargets.clear();
    }

    /** Best effort around the test run — a leftover that survives here is red in the test itself. */
    protected static void deleteTree(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // nothing to recover from before the next test
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not delete " + directory, e);
        }
    }
}
