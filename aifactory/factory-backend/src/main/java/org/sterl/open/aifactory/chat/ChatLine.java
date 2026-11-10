package org.sterl.open.aifactory.chat;

/**
 * The internal read model of the conversation — the component's own shape, filtered out of the core's
 * memory by {@link ConversationRestore}. Mapping to {@code dto/ChatMessageDto} happens in the
 * controller: an internal facade that returns an API class would put the DTO boundary in the wrong
 * place (skill component-architecture, "Die DTO-Grenze liegt an der External Facade").
 *
 * @param origin who produced this line, in the factory's own words — an agent by its name, a person by
 *               the name his message carries, a step of the trace by its tool or the member behind it —
 *               or {@code null} when nobody did; it is always stated by
 *               the code that knows it and never derived from the role (R-WP-4 "niemand rät die Herkunft")
 */
public record ChatLine(String role, String text, String origin) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_JON = "jon";
    /** A turn that failed (R-WP-34) — a line of the conversation that is nobody's speech. */
    public static final String ROLE_ERROR = "error";
    /** One step of Jon's own trace (R-WP-47) — restored with the conversation, never sent live. */
    public static final String ROLE_TOOL = "tool";

    /**
     * The person who typed it, named as his message named him — {@code null} when the line carries no
     * name, which is the whole of "kein Name" and never a reason to invent one (R-WP-25, R-WP-4).
     */
    public static ChatLine user(String text, String origin) {
        return new ChatLine(ROLE_USER, text, origin);
    }

    /** A line of the conversation, from the agent the caller names — Jon or the member that answered. */
    public static ChatLine jon(String text, String origin) {
        return new ChatLine(ROLE_JON, text, origin);
    }

    /**
     * The line a failed turn leaves behind (R-WP-34). {@code origin} is {@code null} because <em>nobody</em>
     * spoke it: the model was never reached, so there is no agent to name — guessing one here would be the
     * one thing R-WP-4 forbids.
     */
    public static ChatLine error(String text) {
        return new ChatLine(ROLE_ERROR, text, null);
    }

    /**
     * One step of the trace Jon's own work leaves behind (R-WP-47, UC-WP-82). {@code origin} says <em>whose</em>
     * tool it was — the member a delegation speaks for, or the core's tool name — and {@code text} is the
     * excerpt of what came back. An empty excerpt is a call whose result the memory does not hold: the step
     * shows its arrival and nothing more, which is the whole of "kein verschluckter Schritt" (UC-WP-82 G2).
     */
    public static ChatLine tool(String text, String origin) {
        return new ChatLine(ROLE_TOOL, text, origin);
    }
}
