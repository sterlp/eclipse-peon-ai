package org.sterl.llmpeon.shared;

/**
 * Liefert das System-Zeilenende als lesbaren, literal-escapten String,
 * der sicher via JSON an ein LLM übertragen werden kann.
 *
 * Wichtig: Der Rückgabewert enthält bewusst die literalen Zeichen
 * '\' 'r' '\' 'n' (bzw. nur '\' 'r' oder '\' 'n'), NICHT die echten
 * Steuerzeichen. Beim JSON-Serialisieren wird nur der Backslash
 * escaped ("\\r\\n" im JSON), und nach dem Parsen auf LLM-Seite bleibt
 * der sichtbare Text "\r\n" erhalten - das Modell erkennt so eindeutig
 * den Default, statt ein unsichtbares Steuerzeichen zu erhalten.
 */
public final class LineSeparatorUtil {

    private LineSeparatorUtil() {
        // utility class
    }

    public static String getDefaultLineSeparatorForLlm() {
        return switch (System.lineSeparator()) {
            case "\r\n" -> "\\r\\n Windows (CRLF)";
            case "\r"   -> "\\r Classic Mac (CR)";
            case "\n"   -> "\\n Unix/Linux (LF)";
            default     -> hexEscape(System.lineSeparator()) + " Unknown as hex encoded";
        };
    }

    private static String hexEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.toString();
    }
}