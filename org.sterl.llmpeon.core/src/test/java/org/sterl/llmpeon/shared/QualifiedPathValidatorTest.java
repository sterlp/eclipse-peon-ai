package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class QualifiedPathValidatorTest {

    private static final Predicate<String> PROJECT_EXISTS = Set.of("test_project")::contains;

    // ------------------------------------------------------------------ disk

    @Test
    void disk_absolutePathAccepted() {
        assertThatCode(() -> QualifiedPathValidator.requireQualifiedDisk("Copy", "/abs/dir/file.txt"))
                .doesNotThrowAnyException();
    }

    @Test
    void disk_relativePathRejectedWithContract() {
        assertThatThrownBy(() -> QualifiedPathValidator.requireQualifiedDisk("Rename", "rel/file.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rename paths must be fully qualified — disk: absolute, eclipse: /project/path (got: rel/file.txt)");
    }

    // ------------------------------------------------------------------ eclipse

    @Test
    void eclipse_qualifiedPathOfExistingProjectAccepted() {
        assertThatCode(() -> QualifiedPathValidator.requireQualifiedEclipse("Copy", "/test_project/src/A.java", PROJECT_EXISTS))
                .doesNotThrowAnyException();
    }

    @Test
    void eclipse_relativePathRejected() {
        assertThatThrownBy(() -> QualifiedPathValidator.requireQualifiedEclipse("Copy", "test_project/file.txt", PROJECT_EXISTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be fully qualified");
    }

    @Test
    void eclipse_singleSegmentProjectRejected() {
        assertThatThrownBy(() -> QualifiedPathValidator.requireQualifiedEclipse("Copy", "/test_project", PROJECT_EXISTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be fully qualified");
    }

    @Test
    void eclipse_unknownProjectRejected() {
        assertThatThrownBy(() -> QualifiedPathValidator.requireQualifiedEclipse("Copy", "/no_such_project/file.txt", PROJECT_EXISTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be fully qualified");
    }

    @Test
    void eclipse_backslashPathRejected() {
        assertThatThrownBy(() -> QualifiedPathValidator.requireQualifiedEclipse("Copy", "\\test_project\\file.txt", PROJECT_EXISTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be fully qualified");
    }
}
