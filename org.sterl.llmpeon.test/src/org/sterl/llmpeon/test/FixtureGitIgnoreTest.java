package org.sterl.llmpeon.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

public class FixtureGitIgnoreTest {

    private static List<String> rules;

    @BeforeClass
    public static void readGitIgnore() throws IOException {
        rules = Files.readAllLines(PeonTestFixture.repoRoot().toPath().resolve(".gitignore"));
    }

    @Test
    public void fixtureFilesAreNotIgnored() {
        assertTrue(rules.contains("!/test_project/.project"));
        assertTrue(rules.contains("!/test_project/.classpath"));
        assertTrue(rules.contains("!/test_project/src/**"));
        assertTrue(rules.contains("!/test_project/.settings/"));
        assertTrue(rules.contains("!/test_project/.settings/*.prefs"));

        assertFalse(isIgnored("test_project/.project"));
        assertFalse(isIgnored("test_project/.classpath"));
        assertFalse(isIgnored("test_project/src/org/sterl/fixture/Alpha.java"));
        assertFalse(isIgnored("test_project/.settings/org.eclipse.core.resources.prefs"));
    }

    @Test
    public void binStaysIgnored() {
        assertTrue(isIgnored("test_project/bin/Alpha.class"));
    }

    private static boolean isIgnored(String path) {
        boolean ignored = false;
        for (String line : rules) {
            String rule = line.trim();
            if (rule.isEmpty() || rule.startsWith("#")) continue;
            boolean negated = rule.startsWith("!");
            if (negated) rule = rule.substring(1);
            if (matches(rule, path)) ignored = !negated;
        }
        return ignored;
    }

    private static boolean matches(String rule, String path) {
        boolean anchored = rule.startsWith("/");
        if (anchored) rule = rule.substring(1);
        boolean directory = rule.endsWith("/");
        if (directory) rule = rule.substring(0, rule.length() - 1);

        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < rule.length(); i++) {
            if (rule.charAt(i) == '*') {
                boolean recursive = i + 1 < rule.length() && rule.charAt(i + 1) == '*';
                regex.append(recursive ? ".*" : "[^/]*");
                if (recursive) i++;
            } else {
                regex.append(Pattern.quote(String.valueOf(rule.charAt(i))));
            }
        }
        String prefix = anchored ? "^" : "(^|.*/)";
        String suffix = directory ? "(/.*)?$" : "$";
        return path.matches(prefix + regex + suffix);
    }
}
