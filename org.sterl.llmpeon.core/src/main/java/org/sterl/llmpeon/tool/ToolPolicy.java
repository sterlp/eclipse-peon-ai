package org.sterl.llmpeon.tool;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Decides whether a tool is enabled for an agent based on an allowlist of name prefixes.
 *
 * <p>Ported from the ai-schulung project. Each non-blank allowlist entry is matched against the
 * tool name:</p>
 * <ul>
 *   <li>{@code *} — enables every tool.</li>
 *   <li>a {@code !}-prefix — <b>negation</b>: after the allowlist evaluation it excludes every
 *       tool whose name starts with the rest of the token (deny wins); a trailing {@code *} on
 *       the token is a wildcard suffix ({@code !debugJava*} excludes the whole {@code debugJava}
 *       family).</li>
 *   <li>otherwise a <b>prefix</b> match: {@code document_} enables {@code document_read},
 *       {@code document_write}, ...; an exact tool name enables only that tool. A trailing
 *       {@code *} on an allow token is a wildcard suffix with the same meaning as in negations
 *       (R-ATC-10): {@code eclipse*} ≡ {@code eclipse} (prefix match); {@code *} alone stays
 *       the All-Token.</li>
 * </ul>
 *
 * <p>Three-tier allow semantics:</p>
 * <ol>
 *   <li>allowlist not null/empty and contains at least one allow token (non-blank, no
 *       {@code !}-prefix): a tool is enabled iff it matches {@code *} or an allow-token prefix;</li>
 *   <li>allowlist not null/empty but contains <b>no allow token</b> (negations only): the base set
 *       is <i>all tools</i>, minus the negations;</li>
 *   <li>{@code null} or an empty / blank-only allowlist enables nothing — the "absent
 *       {@code tools:} = all tools" rule is handled by the caller ({@code CustomAgent} for the
 *       frontmatter path, {@link #parseAllowlist} for the CSV store path), not here.</li>
 * </ol>
 *
 * <p>A bare {@code !} (no prefix) never matches in the deny step — it is a defensive no-op. The
 * UI refuses to persist one (R-ATC-9, see {@link #hasEmptyNegation}); only the custom-agent
 * frontmatter path can reach this matcher with a bare {@code !}, where no validation is possible.</p>
 */
public final class ToolPolicy {

    private ToolPolicy() {}

    public static boolean enables(List<String> allowlist, String toolName) {
        if (allowlist == null || toolName == null) return false;
        // (F1b) blank tokens are strictly ignored — a blank-only list stays "nothing"
        var tokens = allowlist.stream().filter(t -> t != null && !t.isBlank()).toList();
        if (tokens.isEmpty()) return false;

        boolean allowed = tokens.stream()
                .anyMatch(t -> isAllowToken(t) && (t.equals("*") || toolName.startsWith(stripWildcard(t))));
        if (!allowed && tokens.stream().noneMatch(ToolPolicy::isAllowToken)) allowed = true; // negations only = all (list NOT empty!)
        if (!allowed) return false;
        // (F1a) a negation with an empty effective prefix (bare `!`) matches NEVER
        return tokens.stream().noneMatch(t -> isNegationToken(t) && matchesNegation(t, toolName));
    }

    /** Strips a single trailing {@code *} wildcard suffix; unchanged when absent. */
    private static String stripWildcard(String token) {
        return token.endsWith("*") ? token.substring(0, token.length() - 1) : token;
    }

    /** Deny step: prefix match on the token after {@code !}; a trailing {@code *} is a wildcard suffix. */
    private static boolean matchesNegation(String token, String toolName) {
        var prefix = stripWildcard(token.substring(1));
        return !prefix.isEmpty() && toolName.startsWith(prefix);
    }

    /**
     * Parses a raw CSV allowlist (store value {@code llm.agent.<id>.tools}) into tokens.
     *
     * @return the trimmed non-blank tokens, or {@code null} when the input is null/blank/
     *         separator-only (= key absent = all tools)
     */
    public static List<String> parseAllowlist(String csv) {
        if (csv == null) return null;
        var tokens = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
        return tokens.isEmpty() ? null : tokens;
    }

    /**
     * Predicate factory for wiring and preview: allows every name when the CSV is absent/empty,
     * otherwise delegates to {@link #enables} with the parsed tokens.
     */
    public static Predicate<String> nameFilter(String rawCsv) {
        var tokens = parseAllowlist(rawCsv);
        return tokens == null ? n -> true : n -> enables(tokens, n);
    }

    /**
     * UI-validation detector (R-ATC-9): does the token list contain a bare {@code !}? The UI
     * refuses to persist such a list; the matcher itself treats it as a no-op.
     */
    public static boolean hasEmptyNegation(List<String> tokens) {
        return tokens != null && tokens.stream().anyMatch(t -> t != null && t.strip().equals("!"));
    }

    private static boolean isAllowToken(String t) {
        return t != null && !t.isBlank() && !t.startsWith("!");
    }

    private static boolean isNegationToken(String t) {
        return t != null && t.startsWith("!");
    }
}
