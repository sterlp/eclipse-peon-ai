package org.sterl.llmpeon.tool.model;

import java.util.List;

import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;

/**
 * Abstraction for file operations used by AI tools.
 * Implementations may be workspace-aware (Eclipse) or plain filesystem (NIO).
 *
 * <p>Convention: return descriptive error strings for LLM-actionable conditions
 * (e.g. "File not found: X"), throw RuntimeException for infrastructure failures.
 */
public interface FileContext {

    /** Reads file content. Returns error string if not found. */
    String readFile(String path);

    /** Updates existing file. Throws IllegalArgumentException if not found. */
    AiFileUpdate writeFile(String path, String content);

    /** Creates or overwrites a file. Creates parent dirs as needed. */
    String createFile(String path, String content);

    /** Deletes a file. Returns status string. */
    String deleteFile(String path);

    /** Searches for files by name substring. Returns file info strings. */
    List<String> searchFiles(String query);
}
