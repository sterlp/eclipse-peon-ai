package org.sterl.llmpeon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IntegerConverterTest {

    final IntegerConverter subject = new IntegerConverter();

    @Test
    void testNull() {
        assertEquals(0, subject.convert(null));
        assertEquals(0, subject.convert(""));
        assertEquals(0, subject.convert(" "));
    }

    @ParameterizedTest
    @CsvSource(value = {
        ":0",
        "0:0"
    }, delimiter = ':')
    void test(String input, int expected) {
        assertEquals(expected, subject.convert(input));
    }
}
