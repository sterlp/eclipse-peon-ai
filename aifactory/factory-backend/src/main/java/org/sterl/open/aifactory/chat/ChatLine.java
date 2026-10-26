package org.sterl.open.aifactory.chat;

/**
 * The internal read model of the conversation — the component's own shape, filtered out of the core's
 * memory by {@link ProjectRuntime#history()}. Mapping to {@code dto/ChatMessageDto} happens in the
 * controller: an internal facade that returns an API class would put the DTO boundary in the wrong
 * place (skill component-architecture, "Die DTO-Grenze liegt an der External Facade").
 */
public record ChatLine(String role, String text) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_JON = "jon";

    public static ChatLine user(String text) {
        return new ChatLine(ROLE_USER, text);
    }

    public static ChatLine jon(String text) {
        return new ChatLine(ROLE_JON, text);
    }
}
