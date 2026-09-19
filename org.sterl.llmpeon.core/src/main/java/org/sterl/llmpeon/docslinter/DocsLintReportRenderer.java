package org.sterl.llmpeon.docslinter;


class DocsLintReportRenderer {

    String docScanSummary(int lintedDocCount, int skippedDocCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Disk read — unsaved editor changes are not included.");
        sb.append("\n\nScanned: ").append(lintedDocCount + skippedDocCount).append(" doc file(s): ");
        sb.append(lintedDocCount).append(" linted, ");
        sb.append(skippedDocCount).append(" not participating");
        return sb.toString();
    }

    String summary(DocsLintResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(docScanSummary(result.lintedDocs().size(), result.skippedDocs().size()));

        if (result.testsRead()) {
            sb.append("; ").append(result.testSourceFileCount()).append(" test source file(s), ");
            sb.append(result.testSourceFilesWithEvidenceCount()).append(" carrying UC ids");
        } else {
            sb.append("; tests: not read");
        }

        if (!result.sourceRoots().isEmpty()) {
            sb.append("\nSources: ").append(String.join(", ", result.sourceRoots()));
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

    /**
     * Compact one-line status for {@code onTool} (R-DL-20), e.g.
     * {@code lintDocs: 3 docs, 2 findings (1 UNBELEGT_ERLEDIGT)}.
     * The kappa clause appears only for non-zero counts; the full report stays in the return value.
     */
    String statusLine(String toolName, DocsLintResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(toolName).append(": ").append(r.docFileCount()).append(" docs, ");
        sb.append(r.findings().size()).append(" findings");
        long kappa = r.findings().stream()
                .filter(f -> f.type() == FindingType.UNBELEGT_ERLEDIGT)
                .count();
        if (kappa > 0) {
            sb.append(" (").append(kappa).append(" UNBELEGT_ERLEDIGT)");
        }
        return sb.toString();
    }
}
