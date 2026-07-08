package org.sterl.llmpeon;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class AbstractMemoryFileTest {

    protected static FileSystem fs;
    /** tmp directory created once for the test */
    protected static Path tmp;

    @BeforeAll
    static void beforeAll() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        tmp = fs.getPath("/tmp");
        Files.createDirectory(tmp);
    }
    @AfterAll
    static void afterAll() throws IOException {
        tmp = null;
        fs.close();
    }
}
