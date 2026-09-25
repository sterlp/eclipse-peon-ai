package org.sterl.llmpeon.tool.tools;

/**
 * R-TC-8: the shell-confirmation preference has exactly two live values — {@code always}
 * and {@code not-autonomous}. Everything else (null, "", "false", the historic "true",
 * unknown) is UNSET: no confirmation, no migration, no alias.
 */
public enum ShellConfirmationMode {

    ALWAYS,
    NOT_AUTONOMOUS,
    UNSET;

    public static ShellConfirmationMode of(String raw) {
        if (raw == null) {
            return UNSET;
        }
        return switch (raw.trim().toLowerCase()) {
            case "always" -> ALWAYS;
            case "not-autonomous" -> NOT_AUTONOMOUS;
            default -> UNSET;
        };
    }
}
