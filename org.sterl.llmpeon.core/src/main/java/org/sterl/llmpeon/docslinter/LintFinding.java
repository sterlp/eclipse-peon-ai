package org.sterl.llmpeon.docslinter;

import java.util.List;

record LintFinding(FindingType type, String id, String file, int line,
                   List<String> extraLines) {

    LintFinding(FindingType type, String id, String file, int line, List<String> extraLines) {
        this.type = type;
        this.id = id;
        this.file = file;
        this.line = line;
        this.extraLines = extraLines == null ? List.of() : List.copyOf(extraLines);
    }

    LintFinding(FindingType type, String id, String file, int line) {
        this(type, id, file, line, List.of());
    }
}