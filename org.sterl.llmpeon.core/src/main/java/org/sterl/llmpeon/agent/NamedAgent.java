package org.sterl.llmpeon.agent;

/**
 * A named member of an agent's visible team: the flavourful UI label ({@code uiName} — e.g. "Da Boss",
 * "Da Thinka", "Da Mek") paired with the underlying {@link AiAgent}. The header status widget renders
 * the {@code uiName} and pulls {@code isWorking()}/{@code getMemory().getTotalTokenUsed()} live from the
 * agent. The {@code uiName} defined here is the single source for that label (the delegate tool reuses
 * it for its progress line — no duplicated literals). See ADR-0025.
 */
public record NamedAgent(String uiName, AiAgent agent) {}
