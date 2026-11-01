package org.sterl.open.aifactory.chat.dto;

/**
 * One line of an agent's own conversation ({@link AgentTranscriptDto}) — read out of the core's memory,
 * never stored by the factory (R-WP-11).
 *
 * @param kind {@code user} for what the agent was handed, {@code agent} for his own answer,
 *            {@code tool} for a tool he called and for its result
 * @param text the line as the memory holds it, cut at the transcript's per-line limit
 */
public record TranscriptLineDto(String kind, String text) {
}
