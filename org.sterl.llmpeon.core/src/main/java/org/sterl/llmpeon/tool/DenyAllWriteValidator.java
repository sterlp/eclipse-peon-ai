package org.sterl.llmpeon.tool;

/**
 * The {@link WriteValidator#DENY_ALL} implementation. Named rather than a lambda because it has to
 * word its own scope for the prompt — a lambda cannot override
 * {@link WriteValidator#pathScopePrompt()} (docs/write-path-validator.md R6).
 */
public class DenyAllWriteValidator implements WriteValidator {

    @Override
    public void validate(String path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        throw new IllegalArgumentException("Write denied: read-only agent does not allow writing to '" + path + "'.");
    }

    @Override
    public String pathScopePrompt() {
        return "You are read only: you may not write, create or delete any file.";
    }
}
