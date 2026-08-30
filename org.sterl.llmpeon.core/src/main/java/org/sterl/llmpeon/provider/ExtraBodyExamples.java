package org.sterl.llmpeon.provider;

import java.util.List;

/**
 * Paste-ready extra-body examples for the advanced config UI (caching.md R3): the provider
 * hardcodes that used to ship by default are now user opt-in, pasted into the per-agent
 * extra-body JSON field.
 *
 * <ul>
 *   <li><b>GPT</b> — {@code prompt_cache_key}: Azure-OpenAI explicit prompt caching key. On
 *       other OpenAI-compatible endpoints the top-level field is ignored (harmless).</li>
 *   <li><b>Claude</b> — {@code cache_control}: the ephemeral cache marker, effective for Claude
 *       behind OpenAI-compatible gateways (LiteLLM &amp; co.) that forward the field.</li>
 * </ul>
 *
 * <p>Both snippets pass {@link ExtraBody#parse} without reserved-key collisions
 * ({@code model}/{@code messages}/{@code tools}). Content is user-facing (homepage) — keep it
 * minimal and valid.</p>
 */
public final class ExtraBodyExamples {

    /** One named example: {@code name} is the UI label, {@code json} the pasted body. */
    public record Example(String name, String json) {}

    public static final Example GPT = new Example("GPT", "{\"prompt_cache_key\": \"llmpeon\"}");
    public static final Example CLAUDE = new Example("Claude", "{\"cache_control\": {\"type\": \"ephemeral\"}}");

    private ExtraBodyExamples() {
    }

    /** All examples in UI order. */
    public static List<Example> all() {
        return List.of(GPT, CLAUDE);
    }
}
