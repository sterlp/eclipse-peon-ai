package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class DocsLinterFindingsFixtureTest {

    private static final Pattern ID_PATTERN = Pattern.compile(DocsLinterTool.DEFAULT_ID_PATTERN);

    @Test
    void findsEverySupportedFindingTypeInKnownBadFixture() throws IOException {
        Path resourceRoot = Path.of("src/test/resources/docs-linter/findings").toAbsolutePath();
        if (!java.nio.file.Files.isDirectory(resourceRoot)) {
            resourceRoot = Path.of("org.sterl.llmpeon.core/src/test/resources/docs-linter/findings")
                    .toAbsolutePath();
        }

        var result = new DocsLinter().lintWithTests(resourceRoot,
                List.of("docs"), List.of("tests"), null, ID_PATTERN);

        // 1 covered UC
        assertThat(result.useCaseCount()).isGreaterThanOrEqualTo(6);

        // DOPPELT_DEFINIERT
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.DOPPELT_DEFINIERT && f.id().equals("UC-DL-8"));

        // PRAEFIX_FREMD
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.PRAEFIX_FREMD && f.id().equals("R-XX-1"));

        // PRAEFIX_FEHLT
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.PRAEFIX_FEHLT && f.file().contains("prefix-missing"));

        // UC_OHNE_REGEL
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UC_OHNE_REGEL && f.id().equals("UC-DL-9"));

        // STATUS_FEHLT
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.STATUS_FEHLT && f.id().equals("UC-DL-10"));

        // FORM_ABWEICHEND
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.FORM_ABWEICHEND && f.id().equals("UC-DL-11"));

        // UNBELEGT_ERLEDIGT
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UNBELEGT_ERLEDIGT
                        && f.id().equals("UC-DL-12"));

        // UNBELEGT
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UNBELEGT
                        && f.id().equals("UC-DL-13"));

        // VERWAIST
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.VERWAIST && f.id().equals("UC-DL-14"));

        // Covered UC should have no UNBELEGT* finding
        assertThat(result.findings().stream()
                .noneMatch(f -> (f.type() == FindingType.UNBELEGT_ERLEDIGT
                        || f.type() == FindingType.UNBELEGT)
                        && f.id().equals("UC-DL-15"))).isTrue();

        // Two tests for UC-DL-15
        assertThat(result.testEvidenceCount()).isGreaterThanOrEqualTo(3); // at least 2 for COVERED + 1 ORPHAN
    }
}