package org.sterl.open.aifactory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The context smoke test — on a throwaway factory home, so no build ever reads or writes the
 * developer's {@code ~/.aifactory}.
 */
@SpringBootTest
class AifactoryApplicationTests {

    private static final Path HOME = createHome();

    @Autowired
    FactoryHome home;

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add(FactoryHome.HOME_PROPERTY, () -> HOME.toString());
    }

    /** Pinned, not assumed: without the dynamic property this stays green while writing to ~/.aifactory. */
    @Test
    void contextLoadsWithAThrowawayFactoryHome() {
        assertThat(home.root()).isEqualTo(HOME);
    }

    @AfterAll
    static void removeHome() throws IOException {
        try (Stream<Path> tree = Files.walk(HOME)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** Class initialization, not a {@code @TempDir}: the dynamic property is read before any callback runs. */
    private static Path createHome() {
        try {
            return Files.createTempDirectory("aifactory-home-").toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException("could not create a throwaway factory home", e);
        }
    }
}
