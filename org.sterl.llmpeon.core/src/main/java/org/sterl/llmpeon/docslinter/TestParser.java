package org.sterl.llmpeon.docslinter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class TestParser {

    private static final Pattern FUNC_HEADER = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|native|strictfp|default)\\s+)*"
            + "(?:<[^>]+>\\s+)*"
            + "(?:void|[\\w.]+(?:<[^>]+>)?)\\s+"
            + "(\\w+)\\s*\\("
            + "|^\\s*(?:async\\s+)?function\\s+(\\w+)\\s*\\("
            + "|^\\s*def\\s+(\\w+)\\s*\\("
            + "|^\\s*func\\s+(?:\\([^)]*\\)\\s+)?(\\w+)\\s*\\("
            + "|^\\s*(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s+)?\\(");

    record TestEvidence(String id, String file, int line, String methodName) {}

    List<TestEvidence> parse(Path file, String relativePath, Pattern idPattern) throws IOException {
        List<String> lines;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            lines = reader.lines().toList();
        }

        List<TestEvidence> evidence = new ArrayList<>();
        List<TestEvidence> pendingContext = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            int lineNum = i + 1;
            String line = lines.get(i);

            String commentIds = extractCommentIds(line, idPattern);
            if (commentIds != null) {
                // A new pure ID line finalizes any previous context search and creates
                // its own evidence immediately. Evidence is never overwritten by a later line.
                evidence.addAll(pendingContext);
                pendingContext.clear();
                for (String id : commentIds.split(",\\s*")) {
                    pendingContext.add(new TestEvidence(id.trim(), relativePath, lineNum, null));
                }
                continue;
            }

            if (pendingContext.isEmpty()) {
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("@")) {
                continue; // keep looking for optional method context
            }

            if (isCommentLine(trimmed)) {
                evidence.addAll(pendingContext);
                pendingContext.clear();
                continue;
            }

            String methodName = extractMethodName(line);
            if (methodName != null) {
                for (var e : pendingContext) {
                    evidence.add(new TestEvidence(e.id(), e.file(), e.line(), methodName));
                }
            } else {
                evidence.addAll(pendingContext);
            }
            pendingContext.clear();
        }

        evidence.addAll(pendingContext);
        return evidence;
    }

    private String extractCommentIds(String line, Pattern idPattern) {
        String trimmed = line.trim();
        String commentBody = null;
        if (trimmed.startsWith("//")) {
            commentBody = trimmed.substring(2).trim();
        } else if (trimmed.startsWith("#")) {
            commentBody = trimmed.substring(1).trim();
        } else if (trimmed.startsWith("--")) {
            commentBody = trimmed.substring(2).trim();
        }
        if (commentBody == null) return null;

        String[] parts = commentBody.split(",\\s*");
        for (String part : parts) {
            if (!idPattern.matcher(part).matches()) return null;
        }
        return commentBody;
    }

    private boolean isCommentLine(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--");
    }

    private String extractMethodName(String line) {
        var m = FUNC_HEADER.matcher(line);
        if (m.find()) {
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null) return m.group(g);
            }
        }
        return null;
    }
}
