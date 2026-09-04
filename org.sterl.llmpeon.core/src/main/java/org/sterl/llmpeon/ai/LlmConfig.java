package org.sterl.llmpeon.ai;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.shared.StringUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/**
 * SKILL_DIRECTORY      = skill
 * COMMAND_DIRECTORY    = command
 * AGENT_DIRECTORY      = agent"
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString(exclude = {"apiKey", "headerParams", "extraBody"})
public class LlmConfig {
    
    public final static String SKILL_DIRECTORY      = "skills";
    public final static String COMMAND_DIRECTORY    = "commands";
    public final static String AGENT_DIRECTORY      = "agents";

    @Default
    @NonNull
    private final AiProvider providerType = AiProvider.OLLAMA;
    @Default
    private final String model = null;
    /**
     * Per-agent model configs keyed by agent id ({@code dev}/{@code po}/{@code plan}/{@code search}/{@code compact}).
     * Immutable; missing entries resolve to {@link AgentModelConfig#empty()}. The dev model is the base
     * {@link #model} (no separate dev model key).
     */
    @Default
    private final Map<String, AgentModelConfig> modelConfigs = Map.of();
    @NonNull
    @Default
    private final Duration timeout = Duration.ofMinutes(3);
    @Default
    private final String url = null;
    @Default
    private final int autoCompactAfter = 80000;
    /**
     * Max output tokens per response. 0 = use the provider/library default.
     * Anthropic's langchain4j default is only 1024, which truncates large
     * tool-call JSON mid-stream; set a higher value to avoid this.
     */
    @Default
    private final int maxTokens = 0;
    /**
     * Base/dev model capability. Drives build-time thinking for Gemini/Mistral and the returnThinking
     * context only — the per-agent think value itself now lives in {@link #modelConfigs}.
     */
    @Default
    private final boolean thinkSupported = false;
    /** Global "send thinking back" (build-time). */
    @Default
    private final boolean sendThinkingEnabled = true;
    @Default
    private final String apiKey = null;
    /**
     * Raw extra JSON body (advanced configuration). Transport for the <b>build-time</b> body:
     * the effective build config carries it into the provider's {@code buildModel}. The base
     * config never sets it (only the effective build-config copy does), so base identity
     * comparison stays unaffected.
     */
    @Default
    private final String extraBody = null;
    @Default
    private final Path configDir = Path.of(System.getProperty("user.home"), ".peon");

    @Default
    private final boolean diskToolsEnabled = false;
    @Default
    private final boolean shellCommandConfirmationRequired = false;
    @Default
    private final boolean debugMode = false;
    @Default
    private final boolean showRealtimeAiResponse = true;
    @Default
    private final Map<String, String> queryParams = new LinkedHashMap<>();
    @Default
    private final Map<String, String> headerParams = new LinkedHashMap<>();
    
    /** Dev/default model thinking support (drives build-time thinking for Gemini/Mistral and returnThinking). */
    public boolean isThinkSupported() {
        return thinkSupported;
    }

    /** Resend prior thinking to the model. */
    public boolean shouldWeSendThinkingBackToLLM() {
        return sendThinkingEnabled;
    }

    public static LlmConfig newConfig(String model, String url) {
        return LlmConfig.builder().model(model).url(url).build();
    }
    
    public static LlmConfig newOllama(String model) {
        return LlmConfig.builder().providerType(AiProvider.OLLAMA)
                .model(model).url("http://localhost:11434").build();
    }
    
    public static LlmConfig newLmStudio(String model) {
        return LlmConfig.builder().providerType(AiProvider.LM_STUDIO)
                .model(model).url("http://localhost:1234/v1").build();
    }
    
    public static LlmConfig newOpenAi(String model) {
        return newOpenAi(model, "http://localhost:1234/v1");
    }
    
    public static LlmConfig newOpenAi(String model, String url) {
        return LlmConfig.builder().providerType(AiProvider.OPEN_AI)
                .model(model).url(url).build();
    }

    public static LlmConfig newConfig(AiProvider provider, String model, String url) {
        return LlmConfig.builder()
                .providerType(provider)
                .model(model)
                .url(url)
                .build();
    }

    public ConfiguredChatModel build() {
        return new ConfiguredChatModel(this);
    }

    /**
     * Base provider/url/key with the record's own url/key/extraBody overriding the base. A blank
     * record field inherits the base value (resolved again by {@link EffectiveConnection}).
     */
    private AgentConfig.AgentConfigBuilder agentBuilder(AgentModelConfig rec) {
        return AgentConfig.builder()
                .provider(providerType)
                .url(StringUtil.hasValue(rec.url()) ? rec.url() : url)
                .apiKey(StringUtil.hasValue(rec.apiKey()) ? rec.apiKey() : apiKey)
                .extraBody(StringUtil.stripToNull(rec.extraBody()))
                .temperature(AgentTemperature.parse(rec.temperature()));
    }

    /** The per-agent config for the given agent id; missing entries resolve to {@link AgentModelConfig#empty()}. */
    public AgentModelConfig modelConfigFor(String agentId) {
        return modelConfigs.getOrDefault(agentId, AgentModelConfig.empty());
    }

    /** A copy with the given agent's model config replaced (null removes the entry). */
    public LlmConfig withModelConfig(String agentId, AgentModelConfig config) {
        var updated = new LinkedHashMap<>(modelConfigs);
        if (config == null) {
            updated.remove(agentId);
        } else {
            updated.put(agentId, config);
        }
        return toBuilder().modelConfigs(Map.copyOf(updated)).build();
    }

    /**
     * The effective connection for the given per-agent record (url/key/body as currently set,
     * provider from the base) — used to fetch the model list for exactly that configuration.
     */
    public EffectiveConnection effectiveConnectionFor(AgentModelConfig record) {
        return EffectiveConnection.of(this, agentBuilder(record).build());
    }

    /** Dev agent — always the base {@link #model}; think/url/key/body from the dev record (verbatim). */
    public AgentConfig devAgentConfig() {
        var dev = modelConfigFor(AgentModelConfig.DEV);
        return agentBuilder(dev).model(model)
                .id(AgentModelConfig.DEV)
                .think(dev.think()).build();
    }

    /** PO agent — model falls back to base; remaining fields come from its own record. */
    public AgentConfig poAgentConfig() {
        var po = modelConfigFor(AgentModelConfig.PO);
        return agentBuilder(po).model(StringUtil.hasValue(po.model()) ? po.model() : model)
                .id(AgentModelConfig.PO)
                .think(po.think()).build();
    }

    /** Plan agent — configuration from its own record (model null = provider default). */
    public AgentConfig planAgentConfig() {
        var plan = modelConfigFor(AgentModelConfig.PLAN);
        return agentBuilder(plan).model(plan.model())
                .id(AgentModelConfig.PLAN)
                .think(plan.think()).build();
    }

    /** Compactor — configuration from its own record. */
    public AgentConfig compactAgentConfig() {
        var compact = modelConfigFor(AgentModelConfig.COMPACT);
        return agentBuilder(compact).model(compact.model())
                .id(AgentModelConfig.COMPACT)
                .think(compact.think()).build();
    }

    /** Search sub-agent — configuration from its own record. */
    public AgentConfig searchAgentConfig() {
        var search = modelConfigFor(AgentModelConfig.SEARCH);
        return agentBuilder(search).model(search.model())
                .id(AgentModelConfig.SEARCH)
                .think(search.think()).build();
    }

    /**
     * Custom agent — model/url/key/extraBody from the agent's own {@code AGENT.md} frontmatter
     * record (blank fields inherit the base, resolved by {@link EffectiveConnection}) and think from
     * its own frontmatter triple (no inheritance). Same resolution path as the five core agents;
     * {@code agentId} is the agent's stable name (per-request metadata, e.g. default cache key).
     */
    public AgentConfig customAgentConfig(AgentModelConfig rec, String agentId, boolean supported, String on, String off) {
        return agentBuilder(rec).model(rec.model())
                .id(agentId)
                .think(ThinkResolver.effectiveThink(supported, on, off)).build();
    }

    public LlmConfig withModel(String model) {
        return this.toBuilder().model(model).build();
    }

    public boolean skillFolderExisits() {
        return this.configDir != null && Files.exists(this.configDir.resolve(SKILL_DIRECTORY));
    }
    public boolean commandFolderExisits() {
        return this.configDir != null && Files.exists(this.configDir.resolve(COMMAND_DIRECTORY));
    }
    public boolean agentFolderExisits() {
        return this.configDir != null && Files.exists(this.configDir.resolve(AGENT_DIRECTORY));
    }

    public boolean isReachable(int timeoutMs) {
        if (url == null || url.isBlank()) return false;
        try {
            var uri = URI.create(url);
            int port = uri.getPort() > 0 ? uri.getPort()
                     : "https".equals(uri.getScheme()) ? 443 : 80;
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), timeoutMs);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static LlmConfigBuilder of(AiProvider provider) {
        return LlmConfig.builder().providerType(provider);
    }

    public List<AiModel> listAiModels() {
        return LlmProviders.of(getProviderType()).listAiModels(this);
    }
    
    public List<String> listModels() {
        return LlmProviders.of(getProviderType()).listModels(this);
    }
}
