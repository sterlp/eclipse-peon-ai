package org.sterl.llmpeon.ai;

/**
 * Identity of an effective LLM connection (ADR-0034): provider + endpoint + credential +
 * build-time body.
 *
 * <p>{@code buildTimeBody} is the agent's raw {@code extraBody} only for providers in
 * {@code BUILD_TIME} mode — their body is baked into the model at build time, so it is part
 * of the connection identity (PO decision 2026-08-28). It is {@code null} otherwise:
 * per-request bodies do not change the connection.</p>
 *
 * <p>Values are taken 1:1 from the configuration (no trim/canonicalization); {@code url}
 * and {@code apiKey} normalize {@code null} to {@code ""} for hash stability.</p>
 */
public record ConnectionIdentity(AiProvider provider, String url, String apiKey, String buildTimeBody) {

    public ConnectionIdentity {
        url = url == null ? "" : url;
        apiKey = apiKey == null ? "" : apiKey;
    }

    /** Masked form for logs: the credential and the body content never leave this type (memory #20). */
    @Override
    public String toString() {
        return "ConnectionIdentity[provider=" + provider
                + ", url=" + url
                + ", apiKey=***"
                + ", buildTimeBody=" + (buildTimeBody == null ? "-" : buildTimeBody.length() + " chars")
                + "]";
    }
}
