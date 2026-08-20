package org.sterl.llmpeon.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@EqualsAndHashCode(of = "path")
@Slf4j
public class DiskFileContextItem implements ContextItem {

    private final Path path;

    @Override
    public String render() {
        try {
            if (Files.isRegularFile(path)) {
                return Files.readString(path);
            } else {
                log.debug("Context file not present, skipping: {}", path);
                return null;
            }
        } catch (IOException e) {
            log.error("Failed to load context file: {}", path, e);
            return null;
        }
    }

    @Override
    public String dedupKey() {
        return label() 
                + ":" + System.lineSeparator() + "---" + System.lineSeparator();
    }

    @Override
    public String label() {
        return path.toAbsolutePath().normalize().toString();
    }
}
