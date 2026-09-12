package org.sterl.llmpeon.provider;

import java.util.List;

/**
 * Paste-ready extra-body examples for the advanced config UI (caching.md R3): the provider
 * hardcodes that used to ship by default are now user opt-in, pasted into the per-agent
 * extra-body JSON field.
 * 
 * <p>All snippets pass {@link ExtraBody#parse} without reserved-key collisions
 * ({@code model}/{@code messages}/{@code tools}). Content is user-facing (homepage) — keep it
 * minimal and valid.</p>
 */
public final class ExtraBodyExamples {

    /** One named example: {@code name} is the UI label, {@code json} the pasted body, {@code description} the tooltip. */
    public record Example(String name, String json, String description) {}

    public static final Example GPT = new Example("GPT KV-Cache Key", "{\"prompt_cache_key\": \"llmpeon-agentname\"}",
            "Sets prompt_cache_key for Azure GPT-5* prompt caching");
    public static final Example CLAUDE = new Example("Claude enable KV-Cache", "{\"cache_control\": {\"type\": \"ephemeral\"}}",
            "Sets cache_control for Claude prompt caching (gateway form)");
    public static final Example LLAMA_CPP = new Example("llama.cpp - compact", """
            {
              "chat_template_kwargs": {
                "enable_thinking": false
              },
              "cache_prompt": false
            }
            """,
            "Disables thinking/reasoning in llama.cpp models");

    private ExtraBodyExamples() {
    }

    /** All examples in UI order. */
    public static List<Example> all() {
        return List.of(GPT, CLAUDE, LLAMA_CPP);
    }
}
