package org.sterl.llmpeon.parts.config;

import java.util.List;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.parts.PeonConstants;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class McpPreferenceInitializer extends AbstractPreferenceInitializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<McpServerConfig>> SERVER_LIST_TYPE = new TypeReference<>() {};

    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        defaults.put(PeonConstants.PREF_MCP_SERVERS, "[]");
        defaults.putBoolean(PeonConstants.PREF_MCP_ENABLED, false);
    }

    public static List<McpServerConfig> loadServers() {
        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        String json = prefs.get(PeonConstants.PREF_MCP_SERVERS, "[]");
        try {
            return MAPPER.readValue(json, SERVER_LIST_TYPE);
        } catch (Exception e) {
            // Reset corrupt config so subsequent calls don't keep failing
            prefs.put(PeonConstants.PREF_MCP_SERVERS, "[]");
            try { prefs.flush(); } catch (Exception ignored) {}
            throw new RuntimeException("Corrupt MCP server config was reset. Please re-add your servers.", e);
        }
    }

    public static void saveServers(List<McpServerConfig> servers) {
        try {
            var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
            prefs.put(PeonConstants.PREF_MCP_SERVERS, MAPPER.writeValueAsString(servers));
            prefs.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save MCP server config", e);
        }
    }

    public static boolean isMcpEnabled() {
        return InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID)
                .getBoolean(PeonConstants.PREF_MCP_ENABLED, false);
    }

    public static void setMcpEnabled(boolean enabled) {
        try {
            var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
            prefs.putBoolean(PeonConstants.PREF_MCP_ENABLED, enabled);
            prefs.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save MCP enabled state", e);
        }
    }
}
