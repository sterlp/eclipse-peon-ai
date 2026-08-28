package org.sterl.llmpeon.provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.shared.StringUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses the raw {@code extraBody} JSON string (provider.md R3, caching.md R1a).
 *
 * <p>Providers with {@link ExtraBodyMode#NONE} ignore {@code extraBody} silently — the UI gate
 * ships in a later cycle. Invalid JSON never breaks the call: it is logged and treated as
 * "no body" (Log OR throw: log here).</p>
 */
@Slf4j
public final class ExtraBody {

    /**
     * Top-level keys that must never be overridden via extraBody — they belong to the provider's
     * own request shape (a duplicate would silently win over the declared field, see
     * docs/provider.md / docs/caching.md).
     */
    private static final Set<String> RESERVED_KEYS = Set.of("model", "messages", "tools");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExtraBody() {
    }

    /**
     * Parses a raw JSON object into its entries, stripping the reserved top-level keys.
     * Nested objects are left untouched.
     *
     * @param rawJson the raw JSON string (nullable)
     * @return the parsed entries (possibly empty), or {@code null} for null/blank input,
     *         invalid JSON, or non-object JSON — the latter two are logged as warnings
     */
    @Nullable
    public static Map<String, Object> parse(@Nullable String rawJson) {
        if (StringUtil.hasNoValue(rawJson)) {
            return null;
        }
        try {
            var parsed = MAPPER.readValue(rawJson, Object.class);
            if (!(parsed instanceof Map<?, ?> raw)) {
                log.warn("extraBody is not a JSON object, ignoring: {}", rawJson);
                return null;
            }
            var result = new LinkedHashMap<String, Object>();
            raw.forEach((k, v) -> {
                if (k instanceof String key && !RESERVED_KEYS.contains(key)) {
                    result.put(key, v);
                }
            });
            return result;
        } catch (JsonProcessingException e) {
            log.warn("extraBody is not valid JSON, ignoring: {}", rawJson);
            return null;
        }
    }
}
