package org.sterl.llmpeon.ai;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import com.openai.models.ReasoningEffort;

/**
 * Suggested reasoning values for UI pickers, derived from the sources that actually define them so
 * the list tracks the library and our mapping resources automatically:
 * <ul>
 *   <li>OpenAI effort levels from the OpenAI SDK's {@link ReasoningEffort.Known} enum
 *       ({@code none}/{@code minimal}/{@code low}/{@code medium}/{@code high}/{@code xhigh});</li>
 *   <li>provider on-values from the built-in {@code /thinking/*} mapping files
 *       ({@link ThinkModelMapping#allOnValues()}, e.g. Anthropic {@code enabled}/{@code adaptive});</li>
 *   <li>the generic on/off tokens the resolver understands
 *       ({@link ThinkResolver#onTokens()}/{@link ThinkResolver#offTokens()}, e.g. Ollama {@code true}/{@code false}).</li>
 * </ul>
 *
 * <p>These are only <em>suggestions</em>: the reasoning value is a free-form string (LM Studio and
 * OpenAI-compatible gateways accept arbitrary values), so any typed value is still valid.</p>
 */
public final class ReasoningPresets {

    private ReasoningPresets() {}

    /** Ordered, de-duplicated reasoning-value suggestions. The empty string (= auto) is always first. */
    public static Set<String> values() {
        var out = new LinkedHashSet<String>();
        out.add(""); // auto — let the built-in mapping decide / send nothing
        for (var known : ReasoningEffort.Known.values()) {
            out.add(known.name().toLowerCase(Locale.ROOT));
        }
        out.addAll(ThinkModelMapping.allOnValues());
        out.addAll(ThinkResolver.onTokens());
        out.addAll(ThinkResolver.offTokens());
        return out;
    }

    /** {@link #values()} as an array, convenient for SWT {@code Combo#setItems}. */
    public static String[] toArray() {
        return values().toArray(String[]::new);
    }
}
