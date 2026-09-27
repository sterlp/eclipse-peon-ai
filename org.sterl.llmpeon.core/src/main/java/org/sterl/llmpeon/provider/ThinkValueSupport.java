package org.sterl.llmpeon.provider;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.ai.AiProvider;

/**
 * SWT-free mapping between the per-agent think value (a plain string) and the widget form a
 * provider's {@link ThinkSupport} dictates (provider.md R5). Stateless — unit-testable without a
 * Display.
 *
 * <p>Value space: {@code ""} = unset, {@code "true"} = generic-on (auto), a concrete level or an
 * explicit off token passes through verbatim. The widget forms:
 * <ul>
 *   <li>{@link ThinkSupport.Toggle} → editable combo {@code ["", "true", "false"]}: stored value
 *       = displayed value (verbatim)</li>
 *   <li>{@link ThinkSupport.Values} → combo {@code [Off, Auto] + values}: Off → {@code ""},
 *       Auto → {@code "true"}, else verbatim</li>
 *   <li>{@link ThinkSupport.FreeString} / {@link ThinkSupport.Unknown} → text field: verbatim</li>
 *   <li>{@link ThinkSupport.None} → hidden: always {@code ""}</li>
 * </ul>
 */
public final class ThinkValueSupport {

    /** Combo label for the off entry (stored value {@code ""}). */
    public static final String OFF = "Off";
    /** Combo label for the auto entry (stored value {@code "true"}). */
    public static final String AUTO = "Auto";

    private ThinkValueSupport() {
    }

    /** Fixed combo items for a {@link ThinkSupport.Toggle} form: unset / on / explicit off. */
    public static List<String> toggleItems() {
        return List.of("", "true", "false");
    }

    /** Combo items for a {@link ThinkSupport.Values} form: {@code [Off, Auto] + values} (order kept, dedup). */
    public static List<String> valuesItems(ThinkSupport.Values values) {
        var items = new ArrayList<String>(List.of(OFF, AUTO));
        for (var v : values.values()) {
            if (!items.contains(v)) items.add(v);
        }
        return items;
    }

    /** The combo label to display for a stored value ({@link ThinkSupport.Values} form). */
    public static String valuesDisplay(String stored) {
        if (stored == null || stored.isBlank()) return OFF;
        if ("true".equals(stored)) return AUTO;
        return stored;
    }

    /** The stored value for a selected combo label ({@link ThinkSupport.Values} form). */
    public static String valuesStored(String display) {
        if (OFF.equals(display)) return "";
        if (AUTO.equals(display)) return "true";
        return display;
    }

    /** The extra-body (JSON) widget is visible when the base provider can carry extra body params. */
    public static boolean extraBodyVisible(AiProvider provider) {
        return LlmProviders.of(provider).supportsExtraBody();
    }
}
