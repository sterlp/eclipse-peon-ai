package org.sterl.llmpeon.docslinter;

import java.nio.file.Path;

class DocsLintReportRenderer {

    String docScanSummary(int lintedDocCount, int skippedDocCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Disk read — unsaved editor changes are not included.");
        sb.append("\n\nScanned: ").append(lintedDocCount + skippedDocCount).append(" doc file(s): ");
        sb.append(lintedDocCount).append(" linted, ");
        sb.append(skippedDocCount).append(" not participating");
        return sb.toString();
    }

    String summary(DocsLintResult result, Path effectiveRoot) {
        StringBuilder sb = new StringBuilder();
        sb.append(docScanSummary(result.lintedDocs().size(), result.skippedDocs().size()));

        if (result.testsRead()) {
            sb.append("; ").append(result.testSourceFileCount()).append(" test source file(s), ");
            sb.append(result.testSourceFilesWithEvidenceCount()).append(" carrying UC ids");
        } else {
            sb.append("; tests: not read");
        }

        sb.append("\nUC definitions: ").append(result.useCaseCount());
        sb.append(" / ").append(result.uniqueUseCaseCount());

        if (result.testsRead()) {
            sb.append("; test IDs: ").append(result.testEvidenceCount());
            sb.append(" / ").append(result.uniqueTestIdCount());
        }

        sb.append("; findings: ").append(result.findings().size());

        if (!result.findings().isEmpty()) {
            sb.append("\n");
            for (var f : result.findings()) {
                sb.append("\n  ").append(f.type()).append(" ").append(f.id())
                        .append(" ").append(f.file()).append(":").append(f.line());
            }
        }

        if (!result.skippedDocs().isEmpty()) {
            sb.append("\n\nNot participating (no idPrefix):");
            for (String s : result.skippedDocs()) {
                sb.append("\n  ").append(s);
            }
        }

        return sb.toString();
    }
}
