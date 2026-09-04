package org.sterl.llmpeon.shared;

import java.util.Locale;
import java.util.Set;

public final class TextFileTypes {

    public static final Set<String> EXTENSIONS = Set.of(
            "java", "xml", "json", "yaml", "yml", "properties", "txt", "md",
            "html", "css", "js", "ts", "jsx", "tsx", "sql", "sh", "bat",
            "gradle", "kt", "groovy", "scala", "py", "rb", "php", "c", "h",
            "cpp", "hpp", "rs", "go", "swift", "cfg", "ini", "toml", "csv",
            "mf", "prefs", "product", "target", "project", "classpath", "bnd");

    public static final Set<String> FILE_NAMES = Set.of(
            "dockerfile", "makefile", "jenkinsfile", "gemfile", "rakefile", "procfile");

    private TextFileTypes() {
    }

    public static boolean isTextFile(String fileName) {
        var name = fileName.toLowerCase(Locale.ROOT);
        if (FILE_NAMES.contains(name)) return true;
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    public static String filterHint() {
        return "File type filter: known text extensions and filenames only.";
    }
}
