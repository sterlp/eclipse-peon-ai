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
            String content = Files.readString(path);
            return dedupKey() + content;
        } catch (IOException e) {
            log.error("Failed to load {}", path, e);
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
