package org.sterl.llmpeon.ai;

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

    public static EffectiveConnection of(LlmConfig base, AgentConfig agent) {
        ExtraBodyMode mode = LlmProviders.of(base.getProviderType()).extraBodyMode();
        String url = StringUtil.hasValue(agent.getUrl()) ? agent.getUrl() : base.getUrl();
        String apiKey = StringUtil.hasValue(agent.getApiKey()) ? agent.getApiKey() : base.getApiKey();
        String buildTimeBody = mode == ExtraBodyMode.BUILD_TIME ? agent.getExtraBody() : null;
        return new EffectiveConnection(
                new ConnectionIdentity(base.getProviderType(), url, apiKey, buildTimeBody),
                base.toBuilder().url(url).apiKey(apiKey).extraBody(buildTimeBody).build(),
                mode == ExtraBodyMode.PER_REQUEST ? agent.getExtraBody() : null,
                StringUtil.hasNoValue(agent.getUrl()) && StringUtil.hasNoValue(agent.getApiKey())
                        && StringUtil.hasNoValue(agent.getExtraBody()));
    }
}
