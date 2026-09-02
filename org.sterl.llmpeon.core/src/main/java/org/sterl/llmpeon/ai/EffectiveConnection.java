package org.sterl.llmpeon.ai;

import java.util.Objects;

import org.sterl.llmpeon.provider.ExtraBodyMode;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Effective connection for one agent request (ADR-0034): which endpoint/credential the agent
 * actually talks to, the build config for that connection, and the per-request extra body.
 *
 * <p>Resolution: the provider stays at the base level (PO decision 2026-08-28); {@code url}
 * and {@code apiKey} fall back to the base when the agent carries no value of its own.
 * {@code isBase} marks the inherited default connection (agent without own url/key/body) —
 * those requests keep using the shared base model.</p>
 */
public record EffectiveConnection(ConnectionIdentity identity, LlmConfig buildConfig, String perRequestBody, boolean isBase) {

    /** Masked form for logs: the per-request body content never leaves this type (memory #20). */
    @Override
    public String toString() {
        return "EffectiveConnection[identity=" + identity
                + ", buildConfig=" + buildConfig
                + ", perRequestBody=" + (perRequestBody == null ? "-" : perRequestBody.length() + " chars")
                + ", isBase=" + isBase
                + "]";
    }

    public static EffectiveConnection of(LlmConfig base, AgentConfig agent) {
        ExtraBodyMode mode = LlmProviders.of(base.getProviderType()).extraBodyMode();
        String url = StringUtil.hasValue(agent.getUrl()) ? agent.getUrl() : base.getUrl();
        String apiKey = StringUtil.hasValue(agent.getApiKey()) ? agent.getApiKey() : base.getApiKey();
        String buildTimeBody = mode == ExtraBodyMode.BUILD_TIME ? agent.getExtraBody() : null;
        // Base connection = agent does not effectively change endpoint/credential/body. The dev/plan
        // agent configs carry the base url/key (not null), so compare against the base values — a
        // null-check alone would double-build a second, functionally identical connection.
        boolean isBase = Objects.equals(url, base.getUrl())
                && Objects.equals(apiKey, base.getApiKey())
                && StringUtil.hasNoValue(agent.getExtraBody());
        return new EffectiveConnection(
                new ConnectionIdentity(base.getProviderType(), url, apiKey, buildTimeBody),
                base.toBuilder().url(url).apiKey(apiKey).extraBody(buildTimeBody).build(),
                mode == ExtraBodyMode.PER_REQUEST ? agent.getExtraBody() : null,
                isBase);
    }
}
