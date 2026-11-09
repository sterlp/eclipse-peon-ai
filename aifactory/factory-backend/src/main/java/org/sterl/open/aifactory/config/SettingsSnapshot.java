package org.sterl.open.aifactory.config;

/**
 * Internal read model of {@link FactoryConfigService}. Masking is a type here, not a filter: there is
 * no component a key could sit in, so nothing can be forgotten at serialization time. The component
 * list is pinned by exact allow-list equality in the tests — any added component turns that test red
 * whatever it is called.
 *
 * <p>{@code think} is the dev slot's value, which is what makes the default page and the agent section
 * show one value (R-WP-49). {@code maxTokens} is {@code null} for the core's "the provider decides", so
 * the field reads as empty instead of as a zero nobody typed.</p>
 *
 * <p>{@code headerParams} has to reach the page — its field is prefilled — but its content is an
 * outbound request value: like the key, it never appears in a rendered form of this record.</p>
 */
public record SettingsSnapshot(String provider, String model, String think, String url, int tokenWindow,
        long timeoutSeconds, boolean sendThinking, Integer maxTokens, String queryParams, String headerParams,
        boolean keySet, KeySource keySource) {

    /** Written by hand for one reason: the header text is not printed, only whether there is any. */
    @Override
    public String toString() {
        return "SettingsSnapshot[provider=" + provider + ", model=" + model + ", think=" + think
                + ", url=" + url + ", tokenWindow=" + tokenWindow + ", timeoutSeconds=" + timeoutSeconds
                + ", sendThinking=" + sendThinking + ", maxTokens=" + maxTokens + ", queryParams=" + queryParams
                + ", headerParamsPresent=" + (headerParams != null && !headerParams.isBlank())
                + ", keySet=" + keySet + ", keySource=" + keySource + "]";
    }
}
