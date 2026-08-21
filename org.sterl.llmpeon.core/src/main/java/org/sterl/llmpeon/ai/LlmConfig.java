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
@ToString(exclude = {"apiKey", "headerParams"})
public class LlmConfig {
    
    public final static String SKILL_DIRECTORY      = "skills";
    public final static String COMMAND_DIRECTORY    = "commands";
    public final static String AGENT_DIRECTORY      = "agents";

    @Default
    @NonNull
    private final AiProvider providerType = AiProvider.OLLAMA;
    @Default
    private final String model = null;
    @Default
    private final String planModel = null;
    @Default
    private final String compactModel = null;
    @Default
    private final String searchModel = null;
    @NonNull
    @Default
    private final Duration timeout = Duration.ofMinutes(3);
    @Default
    private final String url = null;
    @Default
    private final int autoCompactAfter = 80000;
    @Default
    private final double planTemperature = 1.0;
    @Default
    private final double devTemperature = 0.6;
    /**
     * Max output tokens per response. 0 = use the provider/library default.
     * Anthropic's langchain4j default is only 1024, which truncates large
     * tool-call JSON mid-stream; set a higher value to avoid this.
     */
    @Default
    private final int maxTokens = 0;
    /** Dev/default model capability. Also drives build-time thinking config where providers require it. */
    @Default
    private final boolean thinkSupported = false;
    /** Dev on-value. Empty -&gt; auto (heuristic). Setting on or off puts the agent in manual mode. */
    @Default
    private final String thinkOnString = null;
    /** Dev off-value. Empty means provider default, except Ollama maps resolved off to {@code think:false}. */
    @Default
    private final String thinkOffString = null;
    /** Plan model capability. */
    @Default
    private final boolean planThinkSupported = false;
    @Default
    private final String planThinkOnString = null;
    @Default
    private final String planThinkOffString = null;
    /** Global "send thinking back" (build-time). */
    @Default
    private final boolean sendThinkingEnabled = true;
    @Default
    private final String apiKey = null;
    @Default
    private final Path configDir = Path.of(System.getProperty("user.home"), ".peon");

    @Default
    private final boolean diskToolsEnabled = false;
    @Default
    private final boolean shellCommandConfirmationRequired = false;
    @Default
    private final boolean debugMode = false;
    @Default
    private final boolean showRealtimeAiResponse = false;
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

    private AgentConfig.AgentConfigBuilder baseAgentConfig() {
        return AgentConfig.builder()
                .provider(providerType)
                .url(url)
                .apiKey(apiKey);
    }

    /** Dev agent (default model) — uses the dev think slot ({@code DEV == GLOBAL == DEFAULT}). */
    public AgentConfig devAgentConfig() {
        return baseAgentConfig().model(model)
                .think(ThinkResolver.effectiveThink(thinkSupported, thinkOnString, thinkOffString))
                .temperature(devTemperature).build();
    }

    /** Plan agent — its own think slot; {@link #planModel} (null = provider default) and plan temperature. */
    public AgentConfig planAgentConfig() {
        return baseAgentConfig().model(planModel)
                .think(ThinkResolver.effectiveThink(planThinkSupported, planThinkOnString, planThinkOffString))
                .temperature(planTemperature).build();
    }

    /** Compactor — never thinks (nothing sent). Mirrors {@link org.sterl.llmpeon.agent.AiCompressorAgent}'s temperature. */
    public AgentConfig compactAgentConfig() {
        return baseAgentConfig().model(compactModel).think(null)
                .temperature(devTemperature < 1.0 ? 0.2 : null).build();
    }

    /** Search sub-agent — never thinks (nothing sent). Mirrors {@link org.sterl.llmpeon.tool.tools.SearchAgentTool}'s temperature. */
    public AgentConfig searchAgentConfig() {
        return baseAgentConfig().model(searchModel).think(null)
                .temperature(devTemperature < 1.0 ? 0.3 : null).build();
    }

    /** Custom agent — think from its own {@code AGENT.md} frontmatter (no inheritance), provider/url/key from here. */
    public AgentConfig customAgentConfig(String agentModel, boolean supported, String on, String off, Double temperature) {
        return baseAgentConfig().model(agentModel)
                .think(ThinkResolver.effectiveThink(supported, on, off))
                .temperature(temperature).build();
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
        return getProviderType().listAiModels(this);
    }
    
    public List<String> listModels() {
        return getProviderType().listModels(this);
    }
}
