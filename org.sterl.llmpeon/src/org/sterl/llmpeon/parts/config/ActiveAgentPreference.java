package org.sterl.llmpeon.parts.config;

import java.util.Optional;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * The agent the user last chose, kept per Eclipse workspace (R1–R5,
 * org.sterl.llmpeon/docs/active-agent-persistence.md).
 *
 * <p>Deliberately not part of {@code LlmConfig}: it is UI state, not a connection or model setting,
 * so a stale, blank or unknown value has to stay harmless — reading it never fails and never
 * replaces the default agent.</p>
 */
public final class ActiveAgentPreference {

    private static final ILog LOG = Platform.getLog(ActiveAgentPreference.class);

    static final String PREF = "llm.activeAgent";

    private final IEclipsePreferences prefs;

    public ActiveAgentPreference(IEclipsePreferences prefs) {
        this.prefs = prefs;
    }

    public static ActiveAgentPreference instance() {
        return new ActiveAgentPreference(InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID));
    }

    public void save(String agentName) {
        if (!StringUtil.hasValue(agentName)) return;
        prefs.put(PREF, agentName);
        flush();
    }

    public Optional<String> load() {
        return Optional.ofNullable(prefs.get(PREF, null)).filter(StringUtil::hasValue);
    }

    private void flush() {
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            // The agent switch itself has already happened by now — a workspace that cannot persist
            // this convenience value must not fail the user's action, so it is reported, not thrown.
            LOG.error("Failed to persist the active agent preference " + PREF, e);
        }
    }
}
