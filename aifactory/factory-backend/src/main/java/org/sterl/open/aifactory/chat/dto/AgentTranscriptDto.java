package org.sterl.open.aifactory.chat.dto;


import java.util.List;

/**
 * The read-only insight into one agent's own chat (R-WP-26, UC-WP-49): a snapshot of the core's RAM,
 * nothing the factory keeps. A restart answers it empty — the memory behind a team member is not
 * persisted, and the factory adds no second store to keep it (R-WP-11).
 *
 * @param name      the agent whose memory this is, by his core name
 * @param truncated whether older lines were dropped by the transcript's line limit
 * @param lines     the lines in the order the memory holds them, never more than the limit
 */
public record AgentTranscriptDto(String name, boolean truncated, List<TranscriptLineDto> lines) {
}
