package org.sterl.llmpeon.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sterl.llmpeon.shared.RegexUtils;

public class EclipseGrepToolUnitTest {

    @Test
    public void testRegexPatternDetection() {
        // WHEN/THEN - verify regex patterns are detected correctly
        assertTrue("class.*Tool should be detected as regex", RegexUtils.isRegexPattern("class.*Tool"));
        assertTrue("alternation should be detected as regex", RegexUtils.isRegexPattern("foo|bar"));
        assertFalse("simple text should not be detected as regex", RegexUtils.isRegexPattern("test_grep_workspace"));
        assertFalse("no special chars", RegexUtils.isRegexPattern("simpleText"));
    }
}
