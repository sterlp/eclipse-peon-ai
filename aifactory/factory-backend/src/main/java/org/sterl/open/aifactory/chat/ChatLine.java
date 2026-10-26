package org.sterl.open.aifactory.chat;

/**
 * The internal read model of the conversation — the component's own shape, filtered out of the core's
 * memory by {@link ProjectRuntime#history()}. Mapping to {@code dto/ChatMessageDto} happens in the
 * controller: an internal facade that returns an API class would put the DTO boundary in the wrong
 * place (skill component-architecture, "Die DTO-Grenze liegt an der External Facade").
 *
 * @param origin who produced this line, in the factory's own words, or {@code null} when no agent did —
 *               the name is always stated by the code that knows it and never derived from the role
 *               (R-WP-4 "niemand rät die Herkunft")
 */
public record ChatLine(String role, String text, String origin) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_JON = "jon";

    /** The person who typed it. No name is given: a user concept does not exist yet (R-WP-17, Inc 6). */
    public static ChatLine user(String text) {
        return new ChatLine(ROLE_USER, text, null);
    }

    /** A line of the conversation, from the agent the caller names — Jon or the member that answered. */
    public static ChatLine jon(String text, String origin) {
        return new ChatLine(ROLE_JON, text, origin);
    }
}
