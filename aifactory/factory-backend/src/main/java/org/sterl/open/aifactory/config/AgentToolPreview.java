package org.sterl.open.aifactory.config;

import java.util.List;

/**
 * Which tools a slot would run with a given allowlist (R-WP-52) — the config module's port, because the
 * answer needs an agent instance and only {@code chat} may hold those. The implementation asks exactly
 * one source, the core's {@code AbstractAgent.resolveActiveTools}; a second filter here would drift from
 * what the turn actually executes.
 */
public interface AgentToolPreview {

    List<String> activeTools(String slotId, String rawAllowlist);
}
