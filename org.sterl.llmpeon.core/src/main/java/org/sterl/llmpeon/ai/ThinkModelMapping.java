package org.sterl.llmpeon.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in per-provider "think" mapping: translates a generic <em>on</em> value ({@code true}/
 * {@code on}/{@code yes}) into a provider- and model-specific value, using the resource files under
 * {@code /thinking/<PROVIDER>} (one file per {@link AiProvider}, named after the enum constant).
 *
 * <p>This is only consulted for the generic-on case. A concrete value the user set (e.g.
 * {@code high} via the {@code thinking} advanced string) already carries the meaning and bypasses
 * this mapping.</p>
 *
 * <p>File format — one entry per line, {@code #} starts a comment:</p>
 * <pre>model-pattern | on-value | off-value</pre>
 * The pattern matches the model name case-insensitively as a substring; the first matching line
 * wins. An empty on/off value (or no matching line) means "send nothing".
 */
public final class ThinkModelMapping {

    /** One mapping line: a model-name pattern and its on/off values ({@code null} = send nothing). */
    private record Entry(String pattern, String on, String off) {
        boolean matches(String model) {
            return model != null && model.toLowerCase(Locale.ROOT).contains(pattern);
        }
    }

    /** Cache: provider name -> parsed entries. Empty list = no mapping file / no entries. */
    private static final Map<String, List<Entry>> CACHE = new ConcurrentHashMap<>();

    private ThinkModelMapping() {}

    /** Mapped on-value for the provider/model, or {@code null} when nothing should be sent. */
    public static String resolveOn(AiProvider provider, String model) {
        return find(provider, model, true);
    }

    /** Mapped off-value for the provider/model, or {@code null} when nothing should be sent. */
    public static String resolveOff(AiProvider provider, String model) {
        return find(provider, model, false);
    }

    private static String find(AiProvider provider, String model, boolean on) {
        if (provider == null) return null;
        for (var e : entries(provider)) {
            if (e.matches(model)) return on ? e.on() : e.off();
        }
        return null;
    }

    private static List<Entry> entries(AiProvider provider) {
        return CACHE.computeIfAbsent(provider.name(), ThinkModelMapping::load);
    }

    private static List<Entry> load(String provider) {
        var result = new ArrayList<Entry>();
        var is = ThinkModelMapping.class.getResourceAsStream("/thinking/" + provider);
        if (is == null) return result;
        try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var entry = parse(line);
                if (entry != null) result.add(entry);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read think mapping /thinking/" + provider, e);
        }
        return result;
    }

    private static Entry parse(String rawLine) {
        var line = rawLine.strip();
        if (line.isEmpty() || line.startsWith("#")) return null;

        var parts = line.split("\\|", -1);
        var pattern = parts[0].strip().toLowerCase(Locale.ROOT);
        if (pattern.isEmpty()) return null;

        var on = parts.length > 1 ? stripToNull(parts[1]) : null;
        var off = parts.length > 2 ? stripToNull(parts[2]) : null;
        return new Entry(pattern, on, off);
    }

    private static String stripToNull(String value) {
        var v = value.strip();
        return v.isEmpty() ? null : v;
    }
}
