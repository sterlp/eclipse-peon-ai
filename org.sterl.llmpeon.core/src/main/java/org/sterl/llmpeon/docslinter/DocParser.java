package org.sterl.llmpeon.docslinter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class DocParser {

    private final Pattern idPattern;
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)");
    private static final Pattern RULE_ID_PATTERN = Pattern.compile("^R-[A-Z]+-\\d+");
    private static final Pattern STATUS_EMOJI_PATTERN = Pattern.compile(".*\\s([🚧❌✅])(?:\\s+(.*))?$");

    DocParser(Pattern idPattern) {
        this.idPattern = idPattern;
    }

    DocParseResult parse(Path file, String relativePath) throws IOException {
        List<String> lines;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            lines = reader.lines().toList();
        }

        List<DocDefinition> definitions = new ArrayList<>();
        List<LintFinding> findings = new ArrayList<>();

        String[] prefixHolder = { null };
        boolean inFrontmatter = false;
        Fence currentFence = null;
        int lineNum = 0;
        Pattern bulletIdPattern = Pattern.compile(
                "^\\s*(?:[-*+]|\\d+\\.)\\s+(" + idPattern.pattern() + ")\\b");

        for (String rawLine : lines) {
            lineNum++;
            String line = rawLine.trim();

            if (lineNum == 1 && "---".equals(line)) {
                inFrontmatter = true;
                continue;
            }
            if (inFrontmatter) {
                if ("---".equals(line)) {
                    inFrontmatter = false;
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    if ("idPrefix".equals(key) && !value.isEmpty()) {
                        prefixHolder[0] = value;
                    }
                }
                continue;
            }

            if (currentFence != null) {
                if (closesFence(rawLine, currentFence)) {
                    currentFence = null;
                }
                continue;
            }

            Fence opening = openingFence(rawLine);
            if (opening != null) {
                currentFence = opening;
                continue;
            }

            var hm = HEADING_PATTERN.matcher(rawLine);
            if (hm.matches()) {
                int depth = hm.group(1).length();
                String headingContent = hm.group(2).trim();

                String status = null;
                String cleanContent = headingContent;
                String textAfter = null;
                var sm = STATUS_EMOJI_PATTERN.matcher(headingContent);
                if (sm.matches()) {
                    String emoji = sm.group(1);
                    textAfter = sm.group(2);
                    status = statusFromEmoji(emoji, textAfter);
                    int emojiIdx = headingContent.lastIndexOf(emoji);
                    cleanContent = headingContent.substring(0, emojiIdx).trim();
                }

                String id = extractId(cleanContent);
                if (id == null) {
                    continue;
                }

                if (prefixHolder[0] == null) {
                    findings.add(new LintFinding(FindingType.PRAEFIX_FEHLT, id, relativePath, lineNum));
                }

                boolean manuell = isManuellMarker(textAfter);
                DocDefinition def = makeDefinition(id, prefixHolder[0], relativePath, lineNum, depth, status, manuell);
                if (def != null) {
                    definitions.add(def);
                }
                continue;
            }

            var bm = bulletIdPattern.matcher(rawLine);
            if (bm.find()) {
                findings.add(new LintFinding(FindingType.FORM_ABWEICHEND,
                        bm.group(1), relativePath, lineNum));
            }
        }

        validateStructure(definitions, prefixHolder[0], relativePath, findings);

        return new DocParseResult(definitions, findings, prefixHolder[0] != null);
    }

    private record Fence(char marker, int length) {}

    private Fence openingFence(String rawLine) {
        int leadingSpaces = 0;
        while (leadingSpaces < rawLine.length() && leadingSpaces < 3
                && rawLine.charAt(leadingSpaces) == ' ') {
            leadingSpaces++;
        }
        if (leadingSpaces >= rawLine.length()) {
            return null;
        }
        char marker = rawLine.charAt(leadingSpaces);
        if (marker != '`' && marker != '~') {
            return null;
        }
        int fenceEnd = leadingSpaces;
        while (fenceEnd < rawLine.length() && rawLine.charAt(fenceEnd) == marker) {
            fenceEnd++;
        }
        int length = fenceEnd - leadingSpaces;
        if (length < 3) {
            return null;
        }
        return new Fence(marker, length);
    }

    private boolean closesFence(String rawLine, Fence open) {
        int leadingSpaces = 0;
        while (leadingSpaces < rawLine.length() && leadingSpaces < 3
                && rawLine.charAt(leadingSpaces) == ' ') {
            leadingSpaces++;
        }
        if (leadingSpaces >= rawLine.length()) {
            return false;
        }
        char marker = rawLine.charAt(leadingSpaces);
        if (marker != open.marker()) {
            return false;
        }
        int fenceEnd = leadingSpaces;
        while (fenceEnd < rawLine.length() && rawLine.charAt(fenceEnd) == marker) {
            fenceEnd++;
        }
        int length = fenceEnd - leadingSpaces;
        if (length < open.length()) {
            return false;
        }
        for (int i = fenceEnd; i < rawLine.length(); i++) {
            if (!Character.isWhitespace(rawLine.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String extractId(String headingContent) {
        var ruleMatcher = RULE_ID_PATTERN.matcher(headingContent);
        if (ruleMatcher.find()) return ruleMatcher.group();
        var ucMatcher = idPattern.matcher(headingContent);
        if (ucMatcher.find()) return ucMatcher.group();
        return null;
    }

    private DocDefinition makeDefinition(String id, String prefix, String file, int line,
                                         int depth, String status, boolean manuell) {
        boolean isRule = id.startsWith("R-");
        String defPrefix;
        if (isRule) {
            defPrefix = prefix != null ? prefix : null;
        } else {
            defPrefix = prefix;
        }
        return new DocDefinition(id, defPrefix, file, line, depth, status, null, manuell);
    }

    /**
     * R-DL-22 marker predicate (deliberately loose, PO-confirmed): the text after the status
     * emoji is an optional {@code *}, exactly one (…) parenthesis pair containing the
     * substring {@code manuell} (covers "manuelle"), and an optional closing {@code *}.
     * Anchored so "manuell" cannot match earlier title text.
     */
    private static final Pattern MANUELL_MARKER_PATTERN =
            Pattern.compile("^\\*?\\s*\\([^()]*manuell[^()]*\\)\\s*\\*?$");

    private static boolean isManuellMarker(String textAfter) {
        return textAfter != null && MANUELL_MARKER_PATTERN.matcher(textAfter.trim()).matches();
    }

    private String statusFromEmoji(String emoji, String textAfter) {
        return switch (emoji) {
            case "✅" -> "✅ done";
            case "❌" -> textAfter != null && !textAfter.isEmpty() ? "❌ " + textAfter : "❌ specified";
            case "🚧" -> textAfter != null && !textAfter.isEmpty() ? "🚧 " + textAfter : "🚧 in design";
            default -> null;
        };
    }

    private void validateStructure(List<DocDefinition> definitions, String prefix,
                                   String relativePath, List<LintFinding> findings) {
        DocDefinition lastRule = null;
        for (int i = 0; i < definitions.size(); i++) {
            DocDefinition def = definitions.get(i);
            if (def.id().startsWith("R-")) {
                lastRule = def;
                if (prefix != null && def.prefix() != null
                        && !def.prefix().equals(extractRulePrefix(def.id()))) {
                    findings.add(new LintFinding(FindingType.PRAEFIX_FREMD, def.id(), relativePath, def.line()));
                }
            } else {
                if (lastRule == null) {
                    findings.add(new LintFinding(FindingType.UC_OHNE_REGEL, def.id(), relativePath, def.line()));
                    if (def.status() == null) {
                        findings.add(new LintFinding(FindingType.STATUS_FEHLT, def.id(), relativePath, def.line()));
                    }
                } else if (def.depth() != lastRule.depth() + 1) {
                    findings.add(new LintFinding(FindingType.UC_OHNE_REGEL, def.id(), relativePath, def.line()));
                    if (def.status() == null) {
                        findings.add(new LintFinding(FindingType.STATUS_FEHLT, def.id(), relativePath, def.line()));
                    }
                } else {
                    String effectiveStatus = def.status();
                    if (effectiveStatus == null && lastRule.status() != null) {
                        effectiveStatus = lastRule.status();
                    }
                    if (effectiveStatus == null) {
                        findings.add(new LintFinding(FindingType.STATUS_FEHLT, def.id(), relativePath, def.line()));
                    }
                    // Re-slot with inherited status
                    definitions.set(i, new DocDefinition(def.id(), def.prefix(), def.file(),
                            def.line(), def.depth(), effectiveStatus, lastRule.id(), def.manuell()));
                }
            }
        }
    }

    private static String extractRulePrefix(String ruleId) {
        // R-<FEATURE>-<n> → extract <FEATURE>
        int firstDash = ruleId.indexOf('-');
        int secondDash = ruleId.indexOf('-', firstDash + 1);
        if (firstDash > 0 && secondDash > firstDash) {
            return ruleId.substring(firstDash + 1, secondDash);
        }
        return ruleId;
    }

    record DocParseResult(List<DocDefinition> definitions, List<LintFinding> findings,
                          boolean hasPrefix) {
        DocParseResult {
            definitions = List.copyOf(definitions);
            findings = List.copyOf(findings);
        }
    }
}
