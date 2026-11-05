package org.sterl.llmpeon.shared;

import java.util.Locale;

/**
 * A validated, loaded picture (R-RP-1/2) — see org.sterl.llmpeon.core/docs/read-picture-tool.md.
 * {@code displayPath} is the path in the tool family's own dialect (R-RP-7), never a path to resolve here.
 * Never log or print {@link #base64()}.
 */
public record Picture(String displayPath, String mime, String base64, long sizeBytes) {

    /** The text result of the tool call — the picture itself follows as a user message (R-RP-1). */
    public String resultText() {
        return "📷 " + displayPath + " (" + kb() + ") — picture follows in the next user message";
    }

    public String kb() {
        return String.format(Locale.ROOT, "%.1f KB", sizeBytes / 1024.0);
    }
}
