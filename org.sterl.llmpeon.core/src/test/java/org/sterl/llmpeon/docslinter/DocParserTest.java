package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocParserTest {

    private static final Pattern DEFAULT_PATTERN = Pattern.compile(
            DocsLinterTool.DEFAULT_ID_PATTERN);

    @TempDir
    Path tempDir;

    private DocParser parser;
    private Path docFile;

    @BeforeEach
    void setUp() {
        parser = new DocParser(DEFAULT_PATTERN);
        docFile = tempDir.resolve("test.md");
    }

    // --- UC-DL-5: ignoresUseCaseReferencesOutsideHeadings ---
    @Test
    void ignoresUseCaseReferencesOutsideHeadings() throws IOException {
        Files.writeString(docFile, """
                # R-DL-1 Some Rule

                Some text mentioning UC-DL-5 in passing.

                ## UC-DL-5 A Use Case ✅

                Body text with UC-DL-99 reference.
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.definitions()).hasSize(2);
        assertThat(result.definitions().get(0).id()).isEqualTo("R-DL-1");
        assertThat(result.definitions().get(1).id()).isEqualTo("UC-DL-5");
        // UC-DL-99 is a reference, not a definition
        assertThat(result.findings().stream().filter(f -> f.id().equals("UC-DL-99"))).isEmpty();
    }

    // --- UC-DL-7: inheritsRuleStatusWhenUseCaseHasNone ---
    @Test
    void inheritsRuleStatusWhenUseCaseHasNone() throws IOException {
        Files.writeString(docFile, """
                # R-DL-1 Rule ✅ done

                ## UC-DL-1 No Status
                """);

        var result = parser.parse(docFile, "test.md");

        var uc = result.definitions().stream()
                .filter(d -> d.id().equals("UC-DL-1")).findFirst().orElseThrow();
        assertThat(uc.status()).isEqualTo("✅ done");
    }

    // --- UC-DL-8: useCaseStatusOverridesInheritedRuleStatus ---
    @Test
    void useCaseStatusOverridesInheritedRuleStatus() throws IOException {
        Files.writeString(docFile, """
                # R-DL-1 Rule ✅ done

                ## UC-DL-1 This one ❌ specified
                """);

        var result = parser.parse(docFile, "test.md");

        var uc = result.definitions().stream()
                .filter(d -> d.id().equals("UC-DL-1")).findFirst().orElseThrow();
        assertThat(uc.status()).isEqualTo("❌ specified");
    }

    // --- UC-DL-32: readsIdPrefixOnlyInsideLeadingFrontmatter ---
    @Test
    void readsIdPrefixOnlyInsideLeadingFrontmatter() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ---
                idPrefix: IGNORED
                ---

                ## UC-DL-1 A Case
                """);

        var result = parser.parse(docFile, "test.md");

        var rule = result.definitions().stream()
                .filter(d -> d.id().equals("R-DL-1")).findFirst().orElseThrow();
        assertThat(rule.prefix()).isEqualTo("DL");
        // The second frontmatter block with idPrefix: IGNORED must not affect parsing
    }

    // --- Status extraction ---
    @Test
    void extractsStatusMarkersCorrectly() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Done Story ✅

                ## UC-DL-1 In Design 🚧

                ## UC-DL-2 Specified ❌

                ## UC-DL-3 No Marker
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.definitions()).hasSize(4);
        var r1 = findBy(result, "R-DL-1");
        assertThat(r1.status()).isEqualTo("✅ done");

        var uc1 = findBy(result, "UC-DL-1");
        assertThat(uc1.status()).isEqualTo("🚧 in design");

        var uc2 = findBy(result, "UC-DL-2");
        assertThat(uc2.status()).isEqualTo("❌ specified");

        // UC-DL-3 inherits from rule → ✅ done
        var uc3 = findBy(result, "UC-DL-3");
        assertThat(uc3.status()).isEqualTo("✅ done");
    }

    // --- PRAEFIX_FEHLT ---
    @Test
    void reportsMissingPrefixWhenDocDefinesUseCases() throws IOException {
        Files.writeString(docFile, """
                # R-DL-1 Rule

                ## UC-DL-1 UC
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.PRAEFIX_FEHLT && f.id().equals("R-DL-1"));
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.PRAEFIX_FEHLT && f.id().equals("UC-DL-1"));
    }

    // --- UC_OHNE_REGEL ---
    @Test
    void reportsUseCaseWithoutParentRule() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                ## UC-DL-1 Orphan UC
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UC_OHNE_REGEL && f.id().equals("UC-DL-1"));
    }

    // --- STATUS_FEHLT ---
    @Test
    void reportsMissingEffectiveStatus() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule Without Status

                ## UC-DL-1 No Status
                """);

        var result = parser.parse(docFile, "test.md");

        // Rule has no status marker → UC cannot inherit
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.STATUS_FEHLT && f.id().equals("UC-DL-1"));
    }

    // --- FORM_ABWEICHEND ---
    @Test
    void reportsUseCaseDefinitionWrittenAsBullet() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                - UC-DL-1 Bullet style
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.FORM_ABWEICHEND && f.id().equals("UC-DL-1"));
    }

    // --- Bullet with asterisk ---
    @Test
    void detectsBulletFormDeviationWithAsterisk() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                * UC-DL-1 asterisk bullet
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.FORM_ABWEICHEND && f.id().equals("UC-DL-1"));
    }

    // --- Bullet with plus ---
    @Test
    void detectsBulletFormDeviationWithPlus() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                + UC-DL-1 plus bullet
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.FORM_ABWEICHEND && f.id().equals("UC-DL-1"));
    }

    // --- Numbered list ---
    @Test
    void detectsNumberedListAsFormDeviation() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                1. UC-DL-1 numbered
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.FORM_ABWEICHEND && f.id().equals("UC-DL-1"));
    }

    // --- PRAEFIX_FREMD ---
    @Test
    void reportsForeignPrefixInHeading() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-XX-1 Foreign Rule
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.PRAEFIX_FREMD && f.id().equals("R-XX-1"));
    }

    // R-DL-23: hyphenated scope — R-O-TEST-1 splits at the last dash (O-TEST) and matches the doc prefix
    @Test
    void acceptsHyphenatedRuleScopeMatchingDocPrefix() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: O-TEST
                ---

                # R-O-TEST-1 Titel
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.definitions()).extracting(DocDefinition::id)
                .containsExactly("R-O-TEST-1");
        assertThat(result.findings()).noneMatch(f -> f.type() == FindingType.PRAEFIX_FREMD);
    }

    // R-DL-23: hyphenated scope — last-dash scope AB-C is foreign to doc prefix DD
    @Test
    void reportsForeignHyphenatedRuleScope() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DD
                ---

                # R-AB-C-1 Foreign
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.PRAEFIX_FREMD && f.id().equals("R-AB-C-1"));
    }

    // --- No definitions → no findings ---
    @Test
    void producesNoDefinitionsForPlainMarkdown() throws IOException {
        Files.writeString(docFile, """
                # Just a regular heading

                Some text.
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.definitions()).isEmpty();
        assertThat(result.findings()).isEmpty();
        assertThat(result.hasPrefix()).isFalse();
    }

    // --- Frontmatter but no prefix → definitions without prefix ---
    @Test
    void handlesFrontmatterWithoutIdPrefix() throws IOException {
        Files.writeString(docFile, """
                ---
                title: Some Doc
                ---

                # R-DL-1 Rule

                ## UC-DL-1 UC
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.hasPrefix()).isFalse();
        assertThat(result.findings()).anyMatch(f -> f.type() == FindingType.PRAEFIX_FEHLT);
    }

    // --- No frontmatter at all ---
    @Test
    void handlesMissingFrontmatter() throws IOException {
        Files.writeString(docFile, """
                # R-DL-1 Rule

                ## UC-DL-1 UC
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.hasPrefix()).isFalse();
    }

    // --- UC-DL-43: ignoresForeignPrefixHeadingAndFrontmatterExampleInsideFence ---
    @Test
    void ignoresForeignPrefixHeadingAndFrontmatterExampleInsideFence() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ```markdown
                ---
                idPrefix: OTHER
                ---

                # R-OTHER-1 Foreign Rule
                ```

                ## UC-DL-43 A Case
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.definitions()).extracting(DocDefinition::id)
                .containsExactly("R-DL-1", "UC-DL-43");
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.PRAEFIX_FREMD && f.id().equals("R-OTHER-1"));
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.PRAEFIX_FEHLT);
    }

    // --- Tilde fence, indentation, and fence-type mismatch ---
    @Test
    void honorsTildeFenceTypeIndentationAndClosingFence() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                   ~~~markdown
                   ```java
                   # R-XX-1 Inside Tilde
                   ## UC-XX-1 Inside
                   - UC-DL-99 bullet
                   ```
                   # R-YY-1 Still inside
                   ~~~

                ## UC-DL-1 After fence
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.definitions()).extracting(DocDefinition::id)
                .containsExactly("R-DL-1", "UC-DL-1");
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.PRAEFIX_FREMD);
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.FORM_ABWEICHEND && f.id().equals("UC-DL-99"));
    }

    // --- Unterminated fence reaches EOF ---
    @Test
    void treatsUnterminatedFenceAsQuotedUntilEof() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ```markdown
                # R-XX-1 Inside
                ## UC-XX-1 Inside
                - UC-DL-99 bullet
                no closing fence
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.definitions()).extracting(DocDefinition::id)
                .containsExactly("R-DL-1");
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.PRAEFIX_FREMD);
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.FORM_ABWEICHEND);
    }

    // --- Longer opener: shorter fence inside does not close ---
    @Test
    void honorsFenceLengthShorterInnerFenceDoesNotClose() throws IOException {
        Files.writeString(docFile, """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ````markdown
                ```java
                # R-XX-1 Inside
                ## UC-XX-1 Inside
                ```
                # R-YY-1 Still inside
                ````

                ## UC-DL-1 After fence
                """);

        var result = parser.parse(docFile, "test.md");

        assertThat(result.definitions()).extracting(DocDefinition::id)
                .containsExactly("R-DL-1", "UC-DL-1");
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.PRAEFIX_FREMD);
    }

    private DocDefinition findBy(DocParser.DocParseResult result, String id) {
        return result.definitions().stream()
                .filter(d -> d.id().equals(id))
                .findFirst().orElseThrow(() -> new AssertionError("Definition not found: " + id));
    }
}