package org.sterl.llmpeon.provider;

import java.util.List;

import org.sterl.llmpeon.ai.AiProvider;

/**
 * SWT-free mapping between the per-agent think value (a plain string) and the widget form a
 * provider's {@link ThinkSupport} dictates (provider.md R5). Stateless — unit-testable without a
 * Display.
 *
 * <p>Value space: blank = unset, any other value is stored and sent verbatim (ADR-0064) — no
 * Off/Auto pseudo-entries. The widget forms:
 * <ul>
 *   <li>{@link ThinkSupport.Toggle} → editable combo {@code ["", "true", "false"]}: stored value
 *       = displayed value (verbatim)</li>
 *   <li>{@link ThinkSupport.Values} → combo of the provider's actual values (no Off/Auto): stored
 *       value = displayed value (verbatim), blank = nothing selected</li>
 *   <li>{@link ThinkSupport.FreeString} / {@link ThinkSupport.Unknown} → text field: verbatim</li>
 *   <li>{@link ThinkSupport.None} → hidden: always blank</li>
 * </ul>
 */
public final class ThinkValueSupport {

    private ThinkValueSupport() {
    }

    /** Fixed combo items for a {@link ThinkSupport.Toggle} form: unset / on / explicit off. */
    public static List<String> toggleItems() {
        return List.of("", "true", "false");
    }

    /** The extra-body (JSON) widget is visible when the base provider can carry extra body params. */
    public static boolean extraBodyVisible(AiProvider provider) {
        return LlmProviders.of(provider).supportsExtraBody();
    }
}
