package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatServiceTest {

    @Test
    void test() {
        assertEquals("", ChatService.trimArgs("{}"));
        assertEquals("args0: Fooobar.java", ChatService.trimArgs("{args0: Fooobar.java}"));
    }

}
