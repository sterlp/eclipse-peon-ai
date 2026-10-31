package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.Test;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.ActiveAgentPreference;

/**
 * Save/load of the remembered agent on a scratch node (R1, R2, R5 —
 * org.sterl.llmpeon/docs/active-agent-persistence.md). The key is pinned as a literal: the workspace
 * preference name IS the contract, so renaming {@code ActiveAgentPreference.PREF} has to fail here.
 */
public class ActiveAgentPreferenceTest {

    private static final String KEY = "llm.activeAgent";

    @Test
    public void savedAgentNameSurvivesARoundtrip() throws Exception {
        var scratch = scratchNode();
        try {
            new ActiveAgentPreference(scratch).save("Plan");

            // read back through a second instance — the class holds no copy of its own
            assertEquals("Plan", new ActiveAgentPreference(scratch).load().orElseThrow());
        } finally {
            scratch.removeNode();
        }
    }

    @Test
    public void blankOrMissingValueLoadsEmpty() throws Exception {
        var scratch = scratchNode();
        try {
            assertTrue("a fresh workspace has no remembered agent",
                    new ActiveAgentPreference(scratch).load().isEmpty());

            scratch.put(KEY, "   ");

            assertTrue("a blank value is no agent either",
                    new ActiveAgentPreference(scratch).load().isEmpty());
        } finally {
            scratch.removeNode();
        }
    }

    @Test
    public void saveIgnoresBlankName() throws Exception {
        var scratch = scratchNode();
        try {
            var subject = new ActiveAgentPreference(scratch);
            subject.save("Plan");

            subject.save("   ");
            subject.save(null);

            assertEquals("an empty choice never overwrites the last known agent",
                    "Plan", scratch.get(KEY, null));
            assertEquals("Plan", subject.load().orElseThrow());
        } finally {
            scratch.removeNode();
        }
    }

    private static IEclipsePreferences scratchNode() {
        return InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID + ".active-agent-" + UUID.randomUUID());
    }
}
