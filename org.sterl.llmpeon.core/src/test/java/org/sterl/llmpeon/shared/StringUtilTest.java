package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StringUtilTest {

    @Test
    void testOffsetToLine() {
        assertEquals("Paul", StringUtil.offsetToLine("""
                Hallo
                Paul
                """, 1));
    }

}
