package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StringMatcherTest {


    @Test
    void testStart_star() {
        StringMatcher subject = new StringMatcher("*pom.xml", true, false);
        
        // GOOD
        assertTrue(subject.match("pom.xml"), "pom.xml");
        assertTrue(subject.match("/foo/bar/pom.xml"), "/foo/bar/pom.xml");
        
        // BAD
        assertFalse(subject.match("/foo/bar/aa.xml"), "/foo/bar/aa.xml");
        assertFalse(subject.match("pom.xmll"), "pom.xmll");
    }

}
