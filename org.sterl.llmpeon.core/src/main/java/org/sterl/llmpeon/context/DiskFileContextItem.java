package org.sterl.llmpeon.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sterl.llmpeon.agent.model.TaggedResult;

@RequiredArgsConstructor
@EqualsAndHashCode(of = "path")
@Slf4j
public class DiskFileContextItem implements ContextItem {

    private final Path path;

    @Override
    public TaggedResult render() {
        try {
            if (Files.isRegularFile(path)) {
                return new TaggedResult(head(), Files.readString(path));
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
        return TaggedResult.PREFIX + head();
    }

    @Override
    public String label() {
        return path.toAbsolutePath().normalize().toString();
    }

    private String head() {
        return "context: " + label();
    }
}
