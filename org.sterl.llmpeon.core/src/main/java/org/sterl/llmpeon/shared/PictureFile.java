package org.sterl.llmpeon.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * The one place that validates and loads a picture (R-RP-2/3, org.sterl.llmpeon.core/docs/read-picture-tool.md).
 * No resize — the bytes go out unchanged.
 */
public final class PictureFile {

    public static final long MAX_BYTES = 4L * 1024 * 1024;

    private static final Map<String, String> MIME = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");

    private static final String SUPPORTED = "png, jpg, jpeg, gif, webp";

    private PictureFile() {}

    /**
     * Extension (case-insensitive) → size cap (before reading) → read + base64.
     * @throws IllegalArgumentException with the user-facing reason; the tool executor turns it into a text result
     */
    public static Picture load(Path file) {
        return load(file, file.toAbsolutePath().normalize().toString());
    }

    /**
     * Same as {@link #load(Path)}; {@code displayPath} is the path in the caller's own dialect (R-RP-7)
     * and is the only path named in the result and in every error text.
     */
    public static Picture load(Path file, String displayPath) {
        var abs = file.toAbsolutePath().normalize();
        var mime = MIME.get(extension(abs));
        if (mime == null) {
            throw new IllegalArgumentException("unsupported picture type: " + displayExtension(abs)
                    + " (" + SUPPORTED + ") — " + displayPath);
        }
        try {
            long size = Files.size(abs);
            if (size > MAX_BYTES) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "picture too large: %.1f MB, max 4 MB — %s", size / (1024.0 * 1024.0), displayPath));
            }
            var bytes = Files.readAllBytes(abs);
            return new Picture(displayPath, mime, Base64.getEncoder().encodeToString(bytes), bytes.length);
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("File not found: " + displayPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read picture: " + displayPath + " (" + e.getMessage() + ")");
        }
    }

    private static String extension(Path file) {
        var name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String displayExtension(Path file) {
        var ext = extension(file);
        return ext.isEmpty() ? "(no extension)" : "." + ext;
    }
}
