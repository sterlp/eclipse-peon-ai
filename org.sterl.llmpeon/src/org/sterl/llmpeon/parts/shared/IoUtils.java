package org.sterl.llmpeon.parts.shared;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

public class IoUtils {

    // StandardCharsets.UTF_8.name() > JDK 7
    public static String toString(InputStream input, String charset) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int length; (length = input.read(buffer)) != -1; ) {
            result.write(buffer, 0, length);
        }
        return result.toString(charset);
    }
    
    public static String readFile(IFile file) {
        try {
            return file.readString();
        } catch (CoreException e) {
            throw new RuntimeException("Failed to read " + file.getFullPath(), e);
        }
    }
}
