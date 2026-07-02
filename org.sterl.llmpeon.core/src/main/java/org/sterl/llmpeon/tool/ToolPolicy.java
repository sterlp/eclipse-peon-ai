package org.sterl.llmpeon.tool;

import java.util.List;

/**
 * Decides whether a tool is enabled for an agent based on an allowlist of name prefixes.
 *
 * <p>Ported from the ai-schulung project. Each allowlist entry is matched against the tool name:</p>
 * <ul>
 *   <li>{@code *} — enables every tool.</li>
 *   <li>otherwise a <b>prefix</b> match: {@code document_} enables {@code document_read},
 *       {@code document_write}, ...; an exact tool name enables only that tool.</li>
 * </ul>
 *
 * <p>An empty allowlist enables nothing. The "absent {@code tools:} = all tools" rule is handled by
 * the caller ({@code CustomAgentService}), not here.</p>
 */
public final class ToolPolicy {

    private ToolPolicy() {}

    public static boolean enables(List<String> allowlist, String toolName) {
        if (allowlist == null || toolName == null) return false;
        for (String entry : allowlist) {
            if (entry == null) continue;
            String token = entry.trim();
            if (token.isEmpty()) continue;
            if (token.equals("*") || toolName.startsWith(token)) return true;
        }
        return false;
    }
}
