package org.sterl.llmpeon.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;
import java.util.TreeMap;

import org.sterl.llmpeon.shared.StringUtil;

/**
 * A {@link LlmConfigStore} over one properties file — the store the web factory uses (the plugin
 * keeps its Eclipse-preferences store).
 *
 * <p>The file is read once, in the constructor; {@link #get} never touches the disk, so a config
 * load stays I/O-free. A missing file is an empty store, never an error.</p>
 *
 * <p>Every write rewrites the <em>whole</em> snapshot, so keys this class does not know — the
 * per-agent {@code llm.agent.<id>.<field>} keys, anything a user adds by hand — survive. Stated
 * limitation: the properties format carries no comments, so a comment line in a hand-edited file is
 * gone after the first write.</p>
 *
 * <p><b>Deterministic bytes.</b> Serialization is hand-written because {@code Properties.store}
 * always emits a {@code #<date>} comment and uses {@code System.lineSeparator()}: a save that
 * changes nothing would then never be byte-identical, and every write would be a diff. Here the
 * bytes are a pure function of the content — keys sorted, one {@code key=value} per line, LF only,
 * no comment, no timestamp. Escaping follows the {@code Properties} convention, applied to key and
 * value alike; over-escaping a separator inside a value is harmless since {@code Properties.load}
 * unescapes it, so a hand-written and a factory-written file round-trip the same way.</p>
 *
 * <p><b>A blank value removes the key</b> — parity with {@link LlmConfigSaver}'s
 * {@code saveOrRemove} (blank means unset), which is also why no caller ever sends a blank here.</p>
 *
 * <p><b>Single writer by contract:</b> {@code synchronized} guards one instance and the factory
 * serialises its own writes; there is no cross-process lock, so a concurrent external editor loses
 * against the last whole-file rewrite.</p>
 */
public class FileLlmConfigStore implements LlmConfigStore {

    /** Owner-only ({@code 600}) — the file holds the provider key. */
    private static final EnumSet<PosixFilePermission> OWNER_ONLY =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path file;

    /** Sorted, so the written bytes depend on the content and on nothing else. */
    private final TreeMap<String, String> values = new TreeMap<>();

    /** Reads {@code file} now; a missing file starts as an empty store. */
    public FileLlmConfigStore(Path file) {
        this.file = file;
        read();
    }

    @Override
    public String get(String key, String defaultValue) {
        var value = values.get(key);
        return StringUtil.hasNoValue(value) ? defaultValue : value;
    }

    @Override
    public synchronized void put(String key, String value) {
        if (StringUtil.hasNoValue(value)) {
            remove(key);
            return;
        }
        values.put(key, value);
        persist();
    }

    @Override
    public synchronized void remove(String key) {
        if (values.remove(key) == null) {
            return;
        }
        persist();
    }

    public Path file() {
        return file;
    }

    private void read() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        var props = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
        for (var key : props.stringPropertyNames()) {
            values.put(key, props.getProperty(key));
        }
    }

    /**
     * Whole snapshot, written atomically: temp in the <em>target</em> directory (required for
     * {@code ATOMIC_MOVE}), then the move, then {@code 600}. A failure deletes the temp and leaves
     * the original file untouched — the reader never sees a half-written config.
     */
    private void persist() {
        var dir = file.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, "config.", ".tmp");
            Files.write(tmp, toBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            moveReplace(tmp, file);
            tightenPermissions();
        } catch (Exception e) {
            if (tmp != null) {
                deleteQuietly(tmp);
            }
            // names the path only, never a value — this message can reach a log or a response body
            throw new UncheckedIOException("could not write " + file,
                    e instanceof IOException io ? io : new IOException(e));
        }
    }

    private byte[] toBytes() {
        var content = new StringBuilder();
        for (var entry : values.entrySet()) {
            content.append(escape(entry.getKey())).append('=').append(escape(entry.getValue())).append('\n');
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The {@code Properties} escape set — {@code \}, {@code \n}, {@code \r}, {@code \t}, the
     * separators {@code =} and {@code :}, the comment starts {@code #} and {@code !}, a space, and
     * every control or non-ASCII character as {@code \\uXXXX} (so the file stays pure ASCII). Applied
     * uniformly to key and value: {@code Properties.load} unescapes all of it, and a positional rule
     * would only add branches that no caller can distinguish.
     */
    private static String escape(String raw) {
        var out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            var c = raw.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '=' -> out.append("\\=");
                case ':' -> out.append("\\:");
                case ' ' -> out.append("\\ ");
                case '#' -> out.append("\\#");
                case '!' -> out.append("\\!");
                default -> {
                    if (c < 0x20 || c >= 0x7f) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Applied after the move, so a file that was already world-readable is tightened by the next
     * write instead of staying open. Skipped where the file system has no posix view (Windows) —
     * there the owner-only promise is the operating system's, not ours.
     */
    private void tightenPermissions() throws IOException {
        if (!file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        Files.setPosixFilePermissions(file, OWNER_ONLY);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort — the failed write is already reported by the caller
        }
    }

}
