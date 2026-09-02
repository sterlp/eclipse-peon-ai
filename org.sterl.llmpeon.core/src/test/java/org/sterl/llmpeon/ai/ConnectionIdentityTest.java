package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * BDD 4–6 (inc-24 §6): the identity's toString is safe for logs — the credential and the
 * build-time body content never appear, provider/url stay readable.
 */
class ConnectionIdentityTest {

    private static final String KEY = "sk-super-secret-key";
    private static final String BODY = "{\"cache_control\":{\"type\":\"ephemeral\"}}";

    @Test
    void toStringMasksApiKeyAndBuildTimeBody() {
        // GIVEN an identity carrying a real credential and a build-time body
        var identity = new ConnectionIdentity(AiProvider.ANTHROPIC, "https://api.anthropic.com", KEY, BODY);
        // WHEN toString
        var rendered = identity.toString();
        // THEN the credential and the body content are masked, provider/url stay readable
        assertThat(rendered)
                .contains("apiKey=***")
                .contains(BODY.length() + " chars")
                .contains("ANTHROPIC")
                .contains("https://api.anthropic.com")
                .doesNotContain(KEY)
                .doesNotContain("cache_control");
    }

    @Test
    void toStringWithoutBodyShowsPlaceholder() {
        // GIVEN an identity without a build-time body (per-request providers)
        var identity = new ConnectionIdentity(AiProvider.OPEN_AI, "http://base:1234/v1", KEY, null);
        // WHEN toString
        var rendered = identity.toString();
        // THEN the body is a placeholder, the key is still masked
        assertThat(rendered)
                .contains("buildTimeBody=-")
                .contains("apiKey=***")
                .doesNotContain(KEY);
    }

    @Test
    void toStringMaskingDoesNotChangeEquality() {
        // GIVEN two equal identities
        var a = new ConnectionIdentity(AiProvider.OPEN_AI, "http://a/v1", KEY, BODY);
        var b = new ConnectionIdentity(AiProvider.OPEN_AI, "http://a/v1", KEY, BODY);
        // WHEN compared
        // THEN equals/hashCode are unaffected by the custom toString (map keying stays stable)
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
