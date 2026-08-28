package org.sterl.llmpeon.provider;

import java.util.List;

/**
 * The form of the <b>per-agent</b> think input a provider can consume (provider.md R5).
 *
 * <p>This describes only the per-request think axis. The build-time global think axis
 * (ADR-0003) deliberately does not belong here.</p>
 */
public sealed interface ThinkSupport
        permits ThinkSupport.Boolean, ThinkSupport.Values, ThinkSupport.FreeString, ThinkSupport.None, ThinkSupport.Unknown {

    /** Record singletons — no separate constant needed. */
    ThinkSupport NONE = new None();
    ThinkSupport UNKNOWN = new Unknown();

    /** Think is a simple on/off switch (e.g. Ollama {@code think:true/false}). */
    record Boolean() implements ThinkSupport {
    }

    /** Think is one of a fixed set of values (e.g. OpenAI-family reasoning effort levels). */
    record Values(List<String> values) implements ThinkSupport {
    }

    /** Think is an arbitrary free-form string (e.g. LM Studio {@code reasoning}). */
    record FreeString() implements ThinkSupport {
    }

    /** The provider has no per-request think input. */
    record None() implements ThinkSupport {
    }

    /** Genuine default for foreign/new providers whose think form is not (yet) known. */
    record Unknown() implements ThinkSupport {
    }
}
