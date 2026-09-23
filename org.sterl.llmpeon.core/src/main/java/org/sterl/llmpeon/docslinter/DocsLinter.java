package org.sterl.llmpeon.docslinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.sterl.llmpeon.shared.RegexUtils;
import org.sterl.llmpeon.shared.TextFileTypes;

class DocsLinter {

    private static final Set<String> NON_CODE_TEXT_EXTENSIONS = Set.of(
            "md", "txt", "json", "xml", "csv", "yaml", "yml",
            "properties", "cfg", "ini", "toml");

    private static final Pattern RULE_NUM_PATTERN = Pattern.compile("^R-([A-Z]+)-(\\d+)");
    private static final Pattern UC_FLAT_NUM_PATTERN = Pattern.compile("^UC-([A-Z]+)-(\\d+)");

    DocsLintResult lint(Path root, List<String> docRoots, Pattern idPattern) throws IOException {
        DocParseData data = parseDocs(root, docRoots, idPattern);
        List<String> sourceRoots = relativeRoots(root, resolveRoots(root, docRoots));
        return new DocsLintResult(data.definitions(), data.findings(),
                data.lintedDocs(), data.skippedDocs(), data.useCaseCount(),
                false, 0, 0, 0, List.of(), sourceRoots);
    }

    /**
     * Root-relative, '/'-normalized directory names of the resolved scan roots (R-DL-19);
     * the root itself renders as ".". Order is kept, duplicates dropped.
     */
    private static List<String> relativeRoots(Path root, List<Path> resolvedRoots) {
        Path rootAbs = root.toAbsolutePath().normalize();
        var seen = new LinkedHashSet<String>();
        for (Path resolved : resolvedRoots) {
            String rel = rootAbs.relativize(resolved).toString().replace('\\', '/');
            seen.add(rel.isEmpty() ? "." : rel);
        }
        return List.copyOf(seen);
    }

    NextIdsResult nextIds(Path root, List<String> docRoots, Pattern idPattern, String prefix)
            throws IOException {
        if (prefix != null && !prefix.matches("[A-Z]+")) {
            throw new IllegalArgumentException("prefix must be uppercase letters only: " + prefix);
        }
        DocParseData data = parseDocs(root, docRoots, idPattern);

        List<DocDefinition> participatingDefs = data.definitions().stream()
                .filter(d -> d.prefix() != null)
                .toList();

        Map<String, int[]> prefixStats = new TreeMap<>();

        for (var def : participatingDefs) {
            if (def.id().startsWith("R-")) {
                var rm = RULE_NUM_PATTERN.matcher(def.id());
                if (rm.matches()) {
                    String pfx = rm.group(1);
                    int num = Integer.parseInt(rm.group(2));
                    prefixStats.compute(pfx, (k, v) -> {
                        if (v == null) return new int[]{num, 0};
                        v[0] = Math.max(v[0], num);
                        return v;
                    });
                }
            } else {
                var um = UC_FLAT_NUM_PATTERN.matcher(def.id());
                if (um.matches()) {
                    String pfx = um.group(1);
                    int num = Integer.parseInt(um.group(2));
                    prefixStats.compute(pfx, (k, v) -> {
                        if (v == null) return new int[]{0, num};
                        v[1] = Math.max(v[1], num);
                        return v;
                    });
                } else {
                    String id = def.id();
                    if (id.startsWith("UC-")) {
                        int firstDash = id.indexOf('-', 3);
                        if (firstDash > 0) {
                            String pfx = id.substring(3, firstDash);
                            int numStart = firstDash + 1;
                            int numEnd = numStart;
                            while (numEnd < id.length() && Character.isDigit(id.charAt(numEnd))) {
                                numEnd++;
                            }
                            if (numEnd > numStart) {
                                int num = Integer.parseInt(id.substring(numStart, numEnd));
                                prefixStats.compute(pfx, (k, v) -> {
                                    if (v == null) return new int[]{0, num};
                                    v[1] = Math.max(v[1], num);
                                    return v;
                                });
                            }
                        }
                    }
                }
            }
        }

        if (prefix != null && !prefix.isBlank()) {
            String wanted = prefix.trim();
            int[] stats = prefixStats.get(wanted);
            if (stats == null) {
                return new NextIdsResult(List.of(new NextIds(wanted, false, 1, 1)),
                        data.lintedDocs().size(), data.skippedDocs().size());
            }
            return new NextIdsResult(
                    List.of(new NextIds(wanted, true, stats[0] + 1, stats[1] + 1)),
                    data.lintedDocs().size(), data.skippedDocs().size());
        }

        List<NextIds> allocations = new ArrayList<>();
        for (var entry : prefixStats.entrySet()) {
            int[] stats = entry.getValue();
            allocations.add(new NextIds(entry.getKey(), true, stats[0] + 1, stats[1] + 1));
        }
        return new NextIdsResult(allocations, data.lintedDocs().size(), data.skippedDocs().size());
    }

    private DocParseData parseDocs(Path root, List<String> docRoots, Pattern idPattern)
            throws IOException {
        List<Path> resolvedRoots = resolveRoots(root, docRoots);
        List<Path> mdFiles = discoverMarkdownFiles(resolvedRoots);
        if (mdFiles.isEmpty()) {
            return new DocParseData(List.of(), List.of(), List.of(), List.of(), 0);
        }

        DocParser parser = new DocParser(idPattern);
        List<DocDefinition> allDefs = new ArrayList<>();
        List<LintFinding> allFindings = new ArrayList<>();
        List<String> lintedDocs = new ArrayList<>();
        List<String> skippedDocs = new ArrayList<>();

        for (Path file : mdFiles) {
            String relativePath = root.relativize(file).toString();
            relativePath = relativePath.replace('\\', '/');
            DocParser.DocParseResult result = parser.parse(file, relativePath);

            if (!result.hasPrefix() && result.definitions().isEmpty()) {
                skippedDocs.add(relativePath);
            } else {
                lintedDocs.add(relativePath);
                allDefs.addAll(result.definitions());
                allFindings.addAll(result.findings());
            }
        }

        var defsById = new LinkedHashMap<String, List<DocDefinition>>();
        for (var def : allDefs) {
            defsById.computeIfAbsent(def.id(), k -> new ArrayList<>()).add(def);
        }
        for (var entry : defsById.entrySet()) {
            List<DocDefinition> occurrences = entry.getValue();
            if (occurrences.size() > 1) {
                DocDefinition primary = occurrences.get(0);
                List<String> extraLines = occurrences.subList(1, occurrences.size()).stream()
                        .map(d -> d.file() + ":" + d.line())
                        .toList();
                allFindings.add(new LintFinding(FindingType.DOPPELT_DEFINIERT, primary.id(),
                        primary.file(), primary.line(), extraLines));
            }
        }

        long ucCount = allDefs.stream().filter(d -> !d.id().startsWith("R-")).count();

        allFindings.sort(Comparator
                .comparingInt((LintFinding f) -> findingPriority(f.type()))
                .thenComparing(LintFinding::file)
                .thenComparingInt(LintFinding::line)
                .thenComparing(LintFinding::id));

        return new DocParseData(allDefs, allFindings, lintedDocs, skippedDocs, (int) ucCount);
    }

    static int findingPriority(FindingType type) {
        return switch (type) {
            case UNBELEGT_ERLEDIGT -> 0;
            case VERWAIST -> 1;
            case DOPPELT_DEFINIERT -> 2;
            case PRAEFIX_FREMD, PRAEFIX_FEHLT, UC_OHNE_REGEL, STATUS_FEHLT, FORM_ABWEICHEND -> 3;
            case UNBELEGT -> 4;
            case MANUELL -> 5;
        };
    }

    DocsLintResult lintWithTests(Path root, List<String> docRoots,
            List<String> testRoots, List<String> testGlobs, Pattern idPattern) throws IOException {

        DocsLintResult docResult = lint(root, docRoots, idPattern);
        List<Path> resolvedDocRoots = resolveRoots(root, docRoots);

        List<Path> resolvedTestRoots = resolveRoots(root, testRoots != null && !testRoots.isEmpty()
                ? testRoots : List.of("."));

        // R-DL-19: doc roots first, then test roots — dedup, order keeping
        List<String> sourceRoots = new ArrayList<>(relativeRoots(root, resolvedDocRoots));
        for (String rel : relativeRoots(root, resolvedTestRoots)) {
            if (!sourceRoots.contains(rel)) sourceRoots.add(rel);
        }

        List<String> effectiveGlobs = buildDefaultGlobs(testGlobs);
        List<Pattern> globPatterns = effectiveGlobs.stream()
                .map(RegexUtils::globToPattern)
                .toList();

        List<Path> testFiles = discoverFilesByGlobs(resolvedTestRoots, root, resolvedDocRoots, globPatterns);

        TestParser testParser = new TestParser();
        List<TestParser.TestEvidence> allEvidence = new ArrayList<>();
        for (Path file : testFiles) {
            String relativePath = root.relativize(file).toString().replace('\\', '/');
            List<TestParser.TestEvidence> evidence = testParser.parse(file, relativePath, idPattern);
            allEvidence.addAll(evidence);
        }
        allEvidence.sort(Comparator
                .comparing(TestParser.TestEvidence::file)
                .thenComparingInt(TestParser.TestEvidence::line)
                .thenComparing(TestParser.TestEvidence::id));

        Map<String, DocDefinition> defById = new LinkedHashMap<>();
        for (var def : docResult.definitions()) {
            if (!def.id().startsWith("R-")) {
                defById.putIfAbsent(def.id(), def);
            }
        }

        Map<String, List<TestParser.TestEvidence>> testById = new LinkedHashMap<>();
        for (var ev : allEvidence) {
            testById.computeIfAbsent(ev.id(), k -> new ArrayList<>()).add(ev);
        }

        List<LintFinding> allFindings = new ArrayList<>(docResult.findings());

        for (var def : docResult.definitions()) {
            if (def.id().startsWith("R-")) continue;
            if (testById.containsKey(def.id())) continue;

            String status = def.status();
            if (status != null && status.startsWith("✅")) {
                if (def.manuell()) {
                    // R-DL-22: manual-verification annotation exempts the UC from the
                    // evidence check — reported as info-level MANUELL, not UNBELEGT_ERLEDIGT
                    allFindings.add(new LintFinding(FindingType.MANUELL, def.id(),
                            def.file(), def.line()));
                } else {
                    allFindings.add(new LintFinding(FindingType.UNBELEGT_ERLEDIGT, def.id(),
                            def.file(), def.line()));
                }
            } else {
                allFindings.add(new LintFinding(FindingType.UNBELEGT, def.id(),
                        def.file(), def.line()));
            }
        }

        for (var entry : testById.entrySet()) {
            String testId = entry.getKey();
            if (!defById.containsKey(testId)) {
                for (var ev : entry.getValue()) {
                    allFindings.add(new LintFinding(FindingType.VERWAIST,
                            testId, ev.file(), ev.line()));
                }
            }
        }

        allFindings.sort(Comparator
                .comparingInt((LintFinding f) -> findingPriority(f.type()))
                .thenComparing(LintFinding::file)
                .thenComparingInt(LintFinding::line)
                .thenComparing(LintFinding::id));

        int uniqueTestIds = testById.size();
        int testSourceFileCount = testFiles.size();

        return new DocsLintResult(docResult.definitions(), allFindings,
                docResult.lintedDocs(), docResult.skippedDocs(),
                docResult.useCaseCount(), true, allEvidence.size(), uniqueTestIds,
                testSourceFileCount, allEvidence, sourceRoots);
    }

    private List<String> buildDefaultGlobs(List<String> testGlobs) {
        if (testGlobs == null || testGlobs.isEmpty()) {
            return defaultCodeGlobs();
        }
        List<String> result = new ArrayList<>();
        for (String g : testGlobs) {
            if (g != null && !g.isBlank()) {
                result.add(g);
            }
        }
        return result.isEmpty() ? defaultCodeGlobs() : result;
    }

    private static List<String> defaultCodeGlobs() {
        return TextFileTypes.EXTENSIONS.stream()
                .filter(ext -> !NON_CODE_TEXT_EXTENSIONS.contains(ext))
                .sorted()
                .map(ext -> "**/*." + ext)
                .toList();
    }

    private List<Path> discoverFilesByGlobs(List<Path> roots, Path projectRoot,
            List<Path> docRoots, List<Pattern> globPatterns) throws IOException {
        LinkedHashSet<Path> seen = new LinkedHashSet<>();
        for (Path searchRoot : roots) {
            try (Stream<Path> walk = Files.walk(searchRoot)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    for (Path docRoot : docRoots) {
                        if (p.startsWith(docRoot)) return;
                    }
                    String relativePath = projectRoot.relativize(p).toString().replace('\\', '/');
                    if (!relativePath.startsWith("/")) relativePath = "/" + relativePath;
                    for (Pattern globPattern : globPatterns) {
                        if (globPattern.matcher(relativePath).matches()) {
                            seen.add(p);
                            break;
                        }
                    }
                });
            }
        }
        List<Path> files = new ArrayList<>(seen);
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private List<Path> resolveRoots(Path root, List<String> docRoots) throws IOException {
        if (docRoots == null || docRoots.isEmpty()) {
            docRoots = List.of("docs");
        }
        var seen = new LinkedHashSet<Path>();
        for (String dr : docRoots) {
            Path resolved = root.resolve(dr).normalize().toAbsolutePath();
            if (!Files.isDirectory(resolved)) {
                throw new IllegalArgumentException("Doc root not found: " + dr + " (resolved: " + resolved + ")");
            }
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException(
                        "Doc root escapes root: " + dr + " resolves to " + resolved);
            }
            seen.add(resolved);
        }
        return List.copyOf(seen);
    }

    private List<Path> discoverMarkdownFiles(List<Path> roots) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                        .forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private record DocParseData(List<DocDefinition> definitions, List<LintFinding> findings,
                                List<String> lintedDocs, List<String> skippedDocs,
                                int useCaseCount) {
        DocParseData {
            definitions = List.copyOf(definitions);
            findings = List.copyOf(findings);
            lintedDocs = List.copyOf(lintedDocs);
            skippedDocs = List.copyOf(skippedDocs);
        }
    }
}
