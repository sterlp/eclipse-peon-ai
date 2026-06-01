package org.sterl.llmpeon.parts.widget;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class TextInputWidgetTest {

    @Test
    public void formatsSingleDroppedFilePath() {
        assertEquals("'/Users/user/project/file.txt'",
                TextInputWidget.formatDroppedFilePaths(List.of("/Users/user/project/file.txt")));
    }

    @Test
    public void formatsMultipleDroppedFilePathsOnSeparateLines() {
        assertEquals("'/tmp/a.txt'\n'/tmp/b.txt'",
                TextInputWidget.formatDroppedFilePaths(List.of("/tmp/a.txt", "/tmp/b.txt")));
    }

    @Test
    public void skipsBlankDroppedFilePaths() {
        assertEquals("'/tmp/a.txt'",
                TextInputWidget.formatDroppedFilePaths(List.of("", "  ", "/tmp/a.txt")));
    }

    @Test
    public void escapesSingleQuotesInDroppedFilePaths() {
        assertEquals("'/tmp/user\\'s file.txt'",
                TextInputWidget.formatDroppedFilePaths(List.of("/tmp/user's file.txt")));
    }

    @Test
    public void readsOsFileTransferPaths() {
        assertEquals(List.of("/tmp/a.txt", "/tmp/b.txt"),
                TextInputWidget.pathsFromDropData(new String[]{ "/tmp/a.txt", "/tmp/b.txt" }));
    }
}
