package org.sterl.llmpeon.parts.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigLoader;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;

public class LlmPreferenceInitializer extends AbstractPreferenceInitializer {
    private static final ILog LOG = Platform.getLog(LlmPreferenceInitializer.class);

    private static final LlmConfig DEFAULT = LlmConfig.newOllama("qwen3.6-27b-i1");
    
    /** Native peon config home. Preferred and created by default. */
    private static final Path PEON_HOME =  Path.of(System.getProperty("user.home"), ".peon");

    @Override
    public void initializeDefaultPreferences() {
        buildConfigDirs();

        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        defaults.put(PeonConstants.PREF_PROVIDER_TYPE, DEFAULT.getProviderType().name());
        defaults.put(PeonConstants.PREF_MODEL, StringUtil.stripToEmpty(DEFAULT.getModel()));
        defaults.put(PeonConstants.PREF_URL, StringUtil.stripToEmpty(DEFAULT.getUrl()));
        defaults.putLong(PeonConstants.PREF_TIMEOUT, DEFAULT.getTimeout().toSeconds());
        
        defaults.putInt(PeonConstants.PREF_MAX_TOKENS, 0);
        defaults.putInt(PeonConstants.PREF_TOKEN_WINDOW, DEFAULT.getAutoCompactAfter());
        defaults.putBoolean(PeonConstants.PREF_THINK_SUPPORTED, DEFAULT.isThinkSupported());
        defaults.putBoolean(PeonConstants.PREF_SEND_THINKING_ENABLED, DEFAULT.isSendThinkingEnabled());
        defaults.put(PeonConstants.PREF_API_KEY, StringUtil.stripToEmpty(DEFAULT.getApiKey()));

        defaults.put(PeonConstants.PREF_CONFIG_DIRECTORY, PEON_HOME.toString());

        defaults.putBoolean(PeonConstants.PREF_DISK_TOOLS_ENABLED, false);
        defaults.put(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "");
        defaults.put(PeonConstants.PREF_PLAN_TEMPERATURE, String.valueOf(DEFAULT.getPlanTemperature()));
        defaults.put(PeonConstants.PREF_DEV_TEMPERATURE, String.valueOf(DEFAULT.getDevTemperature()));
        defaults.put(PeonConstants.PREF_QUERY_PARAMS, "");
        defaults.put(PeonConstants.PREF_HEADER_PARAMS, "");

        defaults.putBoolean(PeonConstants.PREF_SHOW_REALTIME_AI_RESPONSE, true);
    }


    public static LlmConfig buildWithDefaults() {
        buildConfigDirs();

        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        return LlmConfigLoader.load(new EclipseLlmConfigStore(prefs));
    }

    private static void buildConfigDirs() {
        try {
            if (!Files.isDirectory(PEON_HOME)) Files.createDirectories(PEON_HOME);
            if (!Files.isDirectory(PEON_HOME.resolve(LlmConfig.AGENT_DIRECTORY))) Files.createDirectories(PEON_HOME.resolve(LlmConfig.AGENT_DIRECTORY));
            if (!Files.isDirectory(PEON_HOME.resolve(LlmConfig.COMMAND_DIRECTORY))) Files.createDirectories(PEON_HOME.resolve(LlmConfig.COMMAND_DIRECTORY));
            if (!Files.isDirectory(PEON_HOME.resolve(LlmConfig.SKILL_DIRECTORY))) Files.createDirectories(PEON_HOME.resolve(LlmConfig.SKILL_DIRECTORY));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveModel(String model, AiAgent agent) {
        if (model == null) return;
        try {
            agent.setAgentModelName(model);
            if (agent instanceof AiDevAgent || agent instanceof AiScaffoldAgent) {
                var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
                prefs.put(PeonConstants.PREF_MODEL, model);
                prefs.flush();
            } else if (agent instanceof AiPlanAgent || agent instanceof AiPoAgent) {
                // Jon (Peon-PO) shares the plan model slot (see AiPoAgent#setAgentModelName)
                var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
                prefs.put(PeonConstants.PREF_PLAN_MODEL, model);
                prefs.flush();
            }
        } catch (Exception e) {
            LOG.warn("Failed to save model preference", e);
        }
    }

    /** Persist thinking support for the given agent. Returns true if a Dev/Plan pref changed. */
    public static boolean saveThinkSupported(boolean supported, AiAgent agent) {
        try {
            if (agent instanceof AiDevAgent) {
                var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
                prefs.putBoolean(PeonConstants.PREF_THINK_SUPPORTED, supported);
                prefs.flush();
                return true;
            } else if (agent instanceof AiPlanAgent || agent instanceof AiPoAgent) {
                // Jon (Peon-PO) reuses the plan think slot (see AiPoAgent#isThinkSupported)
                var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
                prefs.putBoolean(PeonConstants.PREF_PLAN_THINK_SUPPORTED, supported);
                prefs.flush();
                return true;
            } else if (agent instanceof org.sterl.llmpeon.agent.CustomAgent custom) {
                custom.migrateIfNeeded();
                custom.getAgentFile().setValue(org.sterl.llmpeon.agent.CustomAgent.THINK_SUPPORTED, String.valueOf(supported));
                custom.getAgentFile().save();
            }
        } catch (Exception e) {
            LOG.warn("Failed to save think support", e);
        }
        return false;
    }

    public static void saveGitHubOAuthToken(String token, String enterpriseUrl) {
        try {
            IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
            prefs.put(PeonConstants.PREF_API_KEY, token);
            prefs.put(PeonConstants.PREF_PROVIDER_TYPE, AiProvider.GITHUB_COPILOT.name());
            if (enterpriseUrl != null && !enterpriseUrl.isBlank()) {
                // Store as copilot-api.{domain} so LlmConfig.url is the ready-to-use base URL
                String normalized = enterpriseUrl.replaceAll("^https?://", "").replaceAll("/+$", "");
                prefs.put(PeonConstants.PREF_URL, "https://copilot-api." + normalized);
            } else {
                prefs.put(PeonConstants.PREF_URL, "");
            }
            prefs.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save GitHub OAuth token", e);
        }
    }

}
