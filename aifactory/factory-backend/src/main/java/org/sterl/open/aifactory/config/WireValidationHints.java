package org.sterl.open.aifactory.config;

/**
 * The wording of the wire rules of this component (R-WP-55). One source for the message of a
 * constraint and for the refusal of the same rule inside a service, so a sentence cannot drift apart
 * from itself.
 *
 * <p>Every value is a {@code static final} compile-time constant: that is what lets an annotation
 * default read {@code message = WireValidationHints.PROVIDER_HINT}. And every value names the
 * <em>field</em> and never the submitted value — a refusal body and a log line must not repeat what
 * was typed, least of all a key (R-WP-16, {@code aifactory/AGENTS-DEV.md}).</p>
 *
 * <p>The {@code Wire*} annotations of this package carry their constraint on a record component with
 * {@code @Target(FIELD)} and nothing more: on a record every applicable target is used, so a
 * {@code METHOD} or {@code PARAMETER} target would validate the same value on the accessor and on the
 * canonical constructor too — one wrong value, three answers, against UC-WP-92's one entry per
 * field.</p>
 */
public final class WireValidationHints {

    /** One wording for both doors that take a provider name: the save and the model list. */
    public static final String PROVIDER_HINT = "provider must name one of the supported providers";
    public static final String TOKEN_WINDOW_HINT = "tokenWindow must be a positive number of tokens";
    public static final String TIMEOUT_HINT = "timeoutSeconds must be a positive number of seconds";
    public static final String SEND_THINKING_HINT = "sendThinking must be true or false";
    /** 0 is a legal answer here and means "the provider decides" — unlike the two fields above (R-WP-51). */
    public static final String MAX_TOKENS_HINT = "maxTokens must be a whole number of tokens, or 0 for the "
            + "provider default";
    public static final String QUERY_PARAMS_HINT = "queryParams must be comma separated key=value pairs";
    public static final String HEADER_PARAMS_HINT = "headerParams must be comma separated key=value pairs";
    /**
     * Both at once is no answer at all: the write order would silently delete the key the same request
     * just set, so the request is refused instead of guessed. The agent level states the same rule
     * ({@code AgentSettingsService}) — one sentence, one source.
     */
    public static final String KEY_INTENT_HINT = "apiKey and removeApiKey are two answers — send one";

    private WireValidationHints() {

    }
}
