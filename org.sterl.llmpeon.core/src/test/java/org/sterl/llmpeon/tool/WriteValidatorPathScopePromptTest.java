package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * R6 (docs/write-path-validator.md): every validator words its own scope and the agent prompt only
 * quotes it — one source for the permission. Core tests carry no UC ids, core docs define rules.
 */
class WriteValidatorPathScopePromptTest {

    @Test
    void allowAllSaysNothing() {
        assertThat(WriteValidator.ALLOW_ALL.pathScopePrompt()).isEmpty();
    }

    @Test
    void denyAllNamesItsReadOnlyScope() {
        String scope = WriteValidator.DENY_ALL.pathScopePrompt();

        assertThat(scope).contains("read only");
        // and no writable place to aim at
        assertThat(scope).doesNotContain("/").doesNotContain("docs");
    }

    @Test
    void docsValidatorKeepsTheKnownText() {
        assertThat(WriteValidator.DOCS.pathScopePrompt())
                .isEqualTo(WriteValidator.JON_PATH_SCOPE_PROMPT);
    }

    @Test
    void dynamicRootsNamesExactlyItsOwnRoots() {
        Path configDir = Path.of("/inc4/home");
        Path tmp = Path.of("/inc4/tmp");
        Path openedLater = Path.of("/inc4/projects/petclinic");
        var additional = new ArrayList<>(List.of(tmp));
        DynamicRootsWriteValidator validator =
                new DynamicRootsWriteValidator(configDir, () -> List.copyOf(additional));

        String scope = validator.pathScopePrompt();
        assertThat(scope).contains(configDir.toString(), tmp.toString());
        assertThat(scope).doesNotContain(openedLater.toString());

        // read from validate()'s own source ⇒ a root opened later is in the prompt of the next turn
        additional.add(openedLater);
        assertThat(validator.pathScopePrompt()).contains(openedLater.toString());
    }
}
