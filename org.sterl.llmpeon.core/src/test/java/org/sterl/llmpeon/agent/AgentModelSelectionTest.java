package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.agent.AgentModelSelection.Target;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.ai.LlmConfigStore;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.prompt.PromptYmlParser;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.tool.ToolService;

/**
 * R-ML5 (org.sterl.llmpeon/docs/model-loading.md): the chat's model selector shows and changes
 * the effective model of the active agent. {@link AgentModelSelection} is the routing the selector
 * goes through ({@code PeonAiService.saveActiveAgentModel} → {@link AgentModelSelection#save}).
 * Every save test also checks that the other targets stay untouched.
 */
class AgentModelSelectionTest {

    private static final String BASE_KEY = LlmConfigKeys.MODEL;
    private static final String PO_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_MODEL);
    private static final String PLAN_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_MODEL);
    private static final String REVIEW_KEY = LlmConfigKeys.agentKey(AgentModelConfig.REVIEW, LlmConfigKeys.AGENT_FIELD_MODEL);

    @TempDir
    Path tmp;

    private TestStore store;

    @BeforeEach
    void setUp() {
        store = new TestStore();
        store.put(BASE_KEY, "qwen3");
    }

    // --- display ---

    // R-ML5 BDD 1
    @Test
    void agentWithOwnModelShowsItsOwnModel() {
        // GIVEN base qwen3 and PO with its own claude-sonnet
        var model = model(base().withModelConfig(AgentModelConfig.PO, AgentModelConfig.empty().withModel("claude-sonnet")));

        // WHEN PO is selected
        var selection = AgentModelSelection.of(new AiPoAgent(model, new ToolService()), model.getConfig());

        // THEN its own model is shown, not the base
        assertThat(selection.effectiveModel()).isEqualTo("claude-sonnet");
        assertThat(selection.inherited()).isFalse();
        assertThat(selection.target()).isEqualTo(Target.AGENT_SLOT);
    }

    // R-ML5 BDD 2
    @Test
    void agentWithoutOwnModelShowsInheritedBase() {
        // GIVEN base qwen3 and Plan without its own model
        var model = model(base());

        // WHEN Plan is selected
        var selection = AgentModelSelection.of(new AiPlanAgent(model, new ToolService()), model.getConfig());

        // THEN the base is shown and marked as inherited
        assertThat(selection.effectiveModel()).isEqualTo("qwen3");
        assertThat(selection.ownModel()).isNull();
        assertThat(selection.inherited()).isTrue();
    }

    @Test
    void devShowsTheBaseAsItsOwnModel() {
        var model = model(base());

        var selection = AgentModelSelection.of(new AiDevAgent(model, new ToolService()), model.getConfig());

        assertThat(selection.target()).isEqualTo(Target.BASE);
        assertThat(selection.effectiveModel()).isEqualTo("qwen3");
        assertThat(selection.inherited()).isFalse();
    }

    // --- save routing ---

    // R-ML5 BDD 3
    @Test
    void choosingAModelForPoWritesOnlyThePoSlot() {
        // GIVEN base qwen3, PO active (no own model)
        var model = model(base());
        var po = new AiPoAgent(model, new ToolService());

        // WHEN gpt-5 is chosen
        var saved = AgentModelSelection.save(po, model.getConfig(), store, "gpt-5");

        // THEN only the PO slot is written — the base (and with it Dev) keeps qwen3
        assertThat(saved).isTrue();
        assertThat(store.get(PO_KEY, null)).isEqualTo("gpt-5");
        assertThat(store.get(BASE_KEY, null)).isEqualTo("qwen3");
        assertThat(store.get(PLAN_KEY, null)).isNull();
        assertThat(store.get(REVIEW_KEY, null)).isNull();
    }

    // R-ML5 BDD 4
    @Test
    void choosingAModelForDevWritesTheBase() {
        // GIVEN Dev active, Review without its own model
        var model = model(base());
        var dev = new AiDevAgent(model, new ToolService());

        // WHEN gpt-5 is chosen
        var saved = AgentModelSelection.save(dev, model.getConfig(), store, "gpt-5");

        // THEN the base is written, no agent slot is created (Review keeps inheriting)
        assertThat(saved).isTrue();
        assertThat(store.get(BASE_KEY, null)).isEqualTo("gpt-5");
        assertThat(store.get(REVIEW_KEY, null)).isNull();
        assertThat(store.get(PO_KEY, null)).isNull();
        assertThat(store.get(PLAN_KEY, null)).isNull();
    }

    // R-ML5 BDD 5
    @Test
    void choosingAModelForACustomAgentWritesItsAgentMd() throws IOException {
        // GIVEN custom agent Tester without a model field
        var file = writeAgent("---\nname: Tester\n---\nbody");
        var model = model(base());
        var tester = customAgent(file, model);

        // WHEN gpt-5 is chosen
        var saved = AgentModelSelection.save(tester, model.getConfig(), store, "gpt-5");

        // THEN the AGENT.md carries the model, the base and the slots stay untouched
        assertThat(saved).isTrue();
        assertThat(Files.readString(file)).contains("model: gpt-5");
        assertThat(store.get(BASE_KEY, null)).isEqualTo("qwen3");
        assertThat(store.get(PO_KEY, null)).isNull();
        assertThat(store.get(PLAN_KEY, null)).isNull();
    }

    // R-ML5 BDD 6
    @Test
    void clearingTheOwnModelRemovesTheSlotKey() {
        // GIVEN PO with its own gpt-5
        store.put(PO_KEY, "gpt-5");
        var model = model(base().withModelConfig(AgentModelConfig.PO, AgentModelConfig.empty().withModel("gpt-5")));
        var po = new AiPoAgent(model, new ToolService());

        // WHEN the field is cleared and left
        var saved = AgentModelSelection.save(po, model.getConfig(), store, "");

        // THEN the PO key is removed (PO inherits qwen3 again), the base is untouched
        assertThat(saved).isTrue();
        assertThat(store.containsKey(PO_KEY)).isFalse();
        assertThat(store.get(BASE_KEY, null)).isEqualTo("qwen3");
    }

    // R-ML5 BDD 6 — custom agent
    @Test
    void clearingTheOwnModelOfACustomAgentRemovesTheField() throws IOException {
        // GIVEN custom agent Tester with model m1
        var file = writeAgent("---\nname: Tester\nmodel: m1\n---\nbody");
        var model = model(base());
        var tester = customAgent(file, model);

        // WHEN the field is cleared and left
        var saved = AgentModelSelection.save(tester, model.getConfig(), store, "  ");

        // THEN the model field is gone from the AGENT.md and the agent inherits the base
        assertThat(saved).isTrue();
        assertThat(Files.readString(file)).doesNotContain("model:");
        assertThat(AgentModelSelection.of(tester, model.getConfig()).inherited()).isTrue();
        assertThat(AgentModelSelection.of(tester, model.getConfig()).effectiveModel()).isEqualTo("qwen3");
    }

    // R-ML5 BDD 7
    @Test
    void clearingTheBaseRemovesTheKeyInsteadOfSavingEmpty() {
        // GIVEN Dev active
        var model = model(base());
        var dev = new AiDevAgent(model, new ToolService());

        // WHEN the field is cleared
        var saved = AgentModelSelection.save(dev, model.getConfig(), store, " ");

        // THEN llm.model is removed — never stored as ""
        assertThat(saved).isTrue();
        assertThat(store.containsKey(BASE_KEY)).isFalse();
    }

    // R-ML5 BDD 8
    @Test
    void leavingTheFieldUnchangedCreatesNoOwnModel() {
        // GIVEN Plan inherits the base qwen3
        var model = model(base());
        var plan = new AiPlanAgent(model, new ToolService());

        // WHEN the user clicks in and leaves without a change (also a case variant, R-ML4)
        var saved = AgentModelSelection.save(plan, model.getConfig(), store, "qwen3");
        var savedCaseVariant = AgentModelSelection.save(plan, model.getConfig(), store, "QWEN3");

        // THEN no Plan key is created
        assertThat(saved).isFalse();
        assertThat(savedCaseVariant).isFalse();
        assertThat(store.containsKey(PLAN_KEY)).isFalse();
    }

    @Test
    void agentWithoutOwnModelIsNotSelectable() throws IOException {
        // GIVEN the scaffold agent (no slot, no AGENT.md)
        var model = model(base().toBuilder().configDir(tmp).build());
        var scaffold = new AiScaffoldAgent(model);

        // WHEN a model is chosen
        var selection = AgentModelSelection.of(scaffold, model.getConfig());
        var saved = AgentModelSelection.save(scaffold, model.getConfig(), store, "gpt-5");

        // THEN it shows the base, is not selectable and nothing is written
        assertThat(selection.selectable()).isFalse();
        assertThat(selection.effectiveModel()).isEqualTo("qwen3");
        assertThat(saved).isFalse();
        assertThat(store.get(BASE_KEY, null)).isEqualTo("qwen3");
    }

    // --- model list connection ---

    // R-ML5 BDD 9
    @Test
    void modelListIsFetchedForTheAgentsOwnConnection() throws IOException {
        // GIVEN PO with its own URL and a custom agent with its own URL; base on localhost:11434
        var model = model(LlmConfig.newConfig(AiProvider.OLLAMA, "qwen3", "http://localhost:11434")
                .withModelConfig(AgentModelConfig.PO, new AgentModelConfig("http://po-host:1234", null, null, null, null, null)));
        var po = new AiPoAgent(model, new ToolService());
        var tester = customAgent(writeAgent("---\nname: Tester\nurl: http://tester-host:5678\n---\nbody"), model);
        var dev = new AiDevAgent(model, new ToolService());

        // WHEN the connection record for the list fetch is resolved
        var cfg = model.getConfig();

        // THEN each agent's own connection is used, Dev uses the base connection
        assertThat(cfg.effectiveConnectionFor(AgentModelSelection.connectionRecord(po, cfg)).identity())
                .isNotEqualTo(cfg.effectiveConnectionFor(AgentModelConfig.empty()).identity());
        assertThat(AgentModelSelection.connectionRecord(po, cfg).url()).isEqualTo("http://po-host:1234");
        assertThat(AgentModelSelection.connectionRecord(tester, cfg).url()).isEqualTo("http://tester-host:5678");
        assertThat(AgentModelSelection.connectionRecord(dev, cfg).url()).isNull();
    }

    // --- helpers ---

    private static LlmConfig base() {
        return LlmConfig.newConfig(AiProvider.OLLAMA, "qwen3", "http://localhost:9999");
    }

    private static ConfiguredChatModel model(LlmConfig config) {
        return config.build();
    }

    private Path writeAgent(String content) throws IOException {
        var file = tmp.resolve("Tester.md");
        Files.writeString(file, content);
        return file;
    }

    private static CustomAgent customAgent(Path file, ConfiguredChatModel model) throws IOException {
        return new CustomAgent(PromptYmlParser.parseYml(file), model, new ToolService());
    }

    /** Map-backed store that also answers "key present?" — the R-ML5 "removed, not empty" checks need it. */
    private static final class TestStore implements LlmConfigStore {
        private final java.util.Map<String, String> map = new java.util.HashMap<>();

        @Override
        public String get(String key, String defaultValue) {
            return map.getOrDefault(key, defaultValue);
        }

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }

        @Override
        public void remove(String key) {
            map.remove(key);
        }

        boolean containsKey(String key) {
            return map.containsKey(key);
        }
    }
}
