package org.sterl.llmpeon.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;

/**
 * Disk-based context item.
 * Renders as: "&lt;absolute path&gt;:\n---\n&lt;content&gt;".
 * A missing file or read error renders {@code null} (nothing to inject).
 */
@RequiredArgsConstructor
public class DiskFileContextItem implements ContextItem {

    private final Path path;

    @Override
    public String render() {
        try {
            String content = Files.readString(path);
            return key() + ":\n---\n" + content;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String label() {
        return key();
    }

    @Override
    public String dedupKey() {
        return key();
    }

    private String key() {
        return path.toAbsolutePath().normalize().toString();
    }
}
