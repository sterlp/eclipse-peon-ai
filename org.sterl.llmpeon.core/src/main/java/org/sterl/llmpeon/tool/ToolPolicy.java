package org.sterl.llmpeon.tool;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.sterl.llmpeon.shared.RegexUtils;

/**
 * Decides whether a tool is enabled for an agent based on an allowlist of name prefixes.
 *
 * <p>Ported from the ai-schulung project. Each non-blank allowlist entry is matched against the
 * tool name. A token containing {@code *} is a <b>glob over the whole name</b> (R-ATC-11, case-insensitive,
 * {@code *} = any run of characters); a token without {@code *} is a prefix:</p>
 * <ul>
 *   <li>{@code *} — enables every tool.</li>
 *   <li>a {@code !}-prefix — <b>negation</b>: after the allowlist evaluation it excludes every
 *       tool matching the rest of the token (deny wins); {@code !debugJava*} excludes the whole
 *       {@code debugJava} family, {@code !*ReadPicture} every tool ending in {@code ReadPicture}.
 *       A rest that is empty or only {@code *} ({@code !}, {@code !*}, {@code !**}) matches never.</li>
 *   <li>otherwise a <b>prefix</b> match: {@code document_} enables {@code document_read},
 *       {@code document_write}, ...; an exact tool name enables only that tool. A trailing
 *       {@code *} behaves like the prefix ({@code eclipse*} ≡ {@code eclipse}, R-ATC-10); an inner
 *       {@code *} is a glob ({@code *Read*}); {@code *} and {@code **} are the All-Token.</li>
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

        boolean allowed = tokens.stream().anyMatch(t -> isAllowToken(t) && matches(t, toolName));
        if (!allowed && tokens.stream().noneMatch(ToolPolicy::isAllowToken)) allowed = true; // negations only = all (list NOT empty!)
        if (!allowed) return false;
        return tokens.stream().noneMatch(t -> isNegationToken(t) && matchesNegation(t, toolName));
    }

    private static boolean matches(String token, String toolName) {
        if (token.equals("*")) return true;
        if (token.indexOf('*') >= 0) return RegexUtils.globToPattern(token).matcher(toolName).matches();
        return toolName.startsWith(token);
    }

    /** Deny step: the rest after {@code !} — empty or only {@code *} never matches (R-ATC-9, R-ATC-11). */
    private static boolean matchesNegation(String token, String toolName) {
        var rest = token.substring(1);
        return !rest.replace("*", "").isEmpty() && matches(rest, toolName);
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
