package org.sterl.llmpeon.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class FileUtils {

    public static String readString(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + filePath, e);
        }
    }
    
    public static Optional<Path> findFirst(Path folder, String name) {
        if (name == null) return Optional.empty();
        try {
            return Files.walk(folder)
                .filter(p -> p.toString().toLowerCase().contains(name.toLowerCase()))
                .findFirst();
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk " + folder);
        }
    }
}
