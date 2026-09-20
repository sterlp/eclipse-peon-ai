package org.sterl.llmpeon.parts.tools.debug;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pure functions rendering the Eclipse debug model as pretty JSON (D5).
 * Stateless — the shared {@link ObjectMapper} is the only instance field.
 */
final class DebugJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DebugJson() {
    }

    static String pretty(Object node) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to render debug JSON: " + e.getMessage(), e);
        }
    }
}
