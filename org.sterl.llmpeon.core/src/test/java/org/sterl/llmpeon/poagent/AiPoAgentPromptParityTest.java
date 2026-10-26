package org.sterl.llmpeon.poagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.prompt.PeonPaths;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.tool.DynamicRootsWriteValidator;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;

/**
 * R6 (docs/write-path-validator.md): the system prompt quotes the validator instead of a constant.
 * Byte-parity is pinned for {@code DOCS} (plugin agents must stay untouched) and, sharply, for a
 * {@link DynamicRootsWriteValidator} carrier — the constant could never name two roots, so only that
 * second test fails when the prompt keeps quoting the constant.
 */
class AiPoAgentPromptParityTest {

    private static final String NL = System.lineSeparator();
    private static final String BASE_PROMPT = PeonPaths.resolve(PromptLoader.loadWithDefault("po.md"));
    private static final String DELEGATION_PROMPT = PeonPaths.resolve(PromptLoader.load("po-delegation.md"));

    private AiPoAgent agent(WriteValidator validator) {
        ConfiguredChatModel model = LlmConfig
                .newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        return new AiPoAgent(model, new ToolService()) {
            @Override
            public WriteValidator getWriteValidator() {
                return validator;
            }
        };
    }

    @Test
    void pluginPromptIsByteIdentical() {
        assertThat(agent(WriteValidator.DOCS).getSystemPrompt())
                .isEqualTo(BASE_PROMPT + NL + NL + WriteValidator.JON_PATH_SCOPE_PROMPT + NL + DELEGATION_PROMPT);
    }

    @Test
    void dynamicRootsCarrierNamesItsOwnRootsInThePrompt() {
        Path projectRoot = Path.of("/inc4/projects/petclinic");
        Path tmp = Path.of("/inc4/tmp");

        String prompt = agent(new DynamicRootsWriteValidator(projectRoot, () -> List.of(tmp))).getSystemPrompt();

        assertThat(prompt).contains(projectRoot.toString(), tmp.toString());
        assertThat(prompt).doesNotContain(WriteValidator.JON_PATH_SCOPE_PROMPT);
    }

    @Test
    void allowAllPromptHasNoScopeParagraph() {
        String prompt = agent(WriteValidator.ALLOW_ALL).getSystemPrompt();

        // exact equality is the seam proof: an empty scope may not leave a blank paragraph behind
        // (measured: BASE_PROMPT itself ends with a line separator, so a bare "\n\n" count is blind here)
        assertThat(prompt).isEqualTo(BASE_PROMPT + NL + DELEGATION_PROMPT);
        assertThat(prompt).doesNotContain(WriteValidator.JON_PATH_SCOPE_PROMPT);
    }
}
