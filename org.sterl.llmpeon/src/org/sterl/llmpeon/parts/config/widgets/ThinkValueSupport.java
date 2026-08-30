package org.sterl.llmpeon.parts.config.widgets;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.provider.ThinkSupport;

/**
 * SWT-free mapping between the per-agent think value (a plain string) and the widget form a
 * provider's {@link ThinkSupport} dictates (provider.md R5). Stateless — unit-testable without a
 * Display.
 *
 * <p>Value space: {@code ""} = off, {@code "true"} = generic-on (auto), a concrete level passes
 * through verbatim. The widget forms:
 * <ul>
 *   <li>{@link ThinkSupport.Boolean} → checkbox: on → {@code "true"}, off → {@code ""}</li>
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

    /** Stored value for a {@link ThinkSupport.Boolean} checkbox. */
    public static String booleanValue(boolean on) {
        return on ? "true" : "";
    }

    /** Whether a stored value turns the {@link ThinkSupport.Boolean} checkbox on. */
    public static boolean booleanOn(String stored) {
        return "true".equals(stored);
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
