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
        List<TestParser.TestEvidence> testEvidence) {

    DocsLintResult {
        definitions = List.copyOf(definitions);
        findings = List.copyOf(findings);
        lintedDocs = List.copyOf(lintedDocs);
        skippedDocs = List.copyOf(skippedDocs);
        testEvidence = testEvidence == null ? List.of() : List.copyOf(testEvidence);
    }

    DocsLintResult(List<DocDefinition> definitions, List<LintFinding> findings,
                   List<String> lintedDocs, List<String> skippedDocs, int useCaseCount) {
        this(definitions, findings, lintedDocs, skippedDocs, useCaseCount,
                false, 0, 0, 0, List.of());
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
