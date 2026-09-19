package org.sterl.llmpeon.docslinter;

import java.util.List;

record DocsLintResult(
        List<DocDefinition> definitions,
        List<LintFinding> findings,
        List<String> lintedDocs,
        List<String> skippedDocs,
        int useCaseCount,
        boolean testsRead,
        int testEvidenceCount,
        int uniqueTestIdCount,
        int testSourceFileCount,
        List<TestParser.TestEvidence> testEvidence,
        List<String> sourceRoots) {

    DocsLintResult {
        definitions = List.copyOf(definitions);
        findings = List.copyOf(findings);
        lintedDocs = List.copyOf(lintedDocs);
        skippedDocs = List.copyOf(skippedDocs);
        testEvidence = testEvidence == null ? List.of() : List.copyOf(testEvidence);
        sourceRoots = sourceRoots == null ? List.of() : List.copyOf(sourceRoots);
    }

    /** Compat constructor without source roots (R-DL-19). */
    DocsLintResult(List<DocDefinition> definitions, List<LintFinding> findings,
                   List<String> lintedDocs, List<String> skippedDocs, int useCaseCount) {
        this(definitions, findings, lintedDocs, skippedDocs, useCaseCount,
                false, 0, 0, 0, List.of(), List.of());
    }

    int docFileCount() {
        return lintedDocs.size() + skippedDocs.size();
    }

    int uniqueUseCaseCount() {
        return (int) definitions.stream()
                .filter(d -> !d.id().startsWith("R-"))
                .map(DocDefinition::id)
                .distinct()
                .count();
    }

    int testSourceFilesWithEvidenceCount() {
        return (int) testEvidence.stream()
                .map(TestParser.TestEvidence::file)
                .distinct()
                .count();
    }
}
