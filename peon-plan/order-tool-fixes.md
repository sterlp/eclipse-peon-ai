# Order: two one-line tool fixes (Paul, 2026-11-10, release today)

Branch `web-factory-4`. NO commit, NO push. Minimal: one test per fix, test first (red without fix,
green with fix). Run only the affected test classes. Do not touch docs, prompts or AGENTS*.md.
Unclear? Ask back instead of guessing.

## Fix 1 — eclipseReadProjectProblems file filter misses JDT problems (R-PP-1)

- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/EclipseBuildTool.java:289`
  `file.findMarkers(IMarker.PROBLEM, false, IResource.DEPTH_ZERO)` → `includeSubtypes = true`
  (like the project-wide path at :282). Java compile errors are subtype markers
  (`org.eclipse.jdt.core.problem`), so the filtered mode reports "No problems" for a broken file.
- Test: in `org.sterl.llmpeon.test/.../EclipseBuildToolTest.java` one test that creates a marker of
  type `org.eclipse.jdt.core.problem` (`IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER`) on FILE_A and
  asserts the filtered call (files = FILE_A) contains its message. Mark it `// UC-PP-1`.
  Red with `false`, green with `true`.

## Fix 2 — insertLines at end of a file ending with a line break

- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/shared/FileLines.java:130`
  A file "a\nb\n" splits into [a, b, ""]; appending today yields "a\nb\n\nx" (extra blank line,
  trailing break lost). Append after the last CONTENT line:
  ```java
  int end = total > 1 && lines.get(total - 1).isEmpty() ? total - 1 : total;
  int at = afterLine == null || afterLine > end ? end : Math.max(0, afterLine);
  ```
- Rule (docs already updated by Jon): `org.sterl.llmpeon.core/docs/disk-file-write-tool.md`
  section diskInsertLines: GIVEN "a\nb\n" WHEN insert null / afterLine 2 / afterLine 3 with "x"
  THEN "a\nb\nx\n".
- Test: ONE parameterized test in `FileLinesTest` over afterLine null, 2, 3 → "a\nb\nx\n".
  Existing FileLinesTest tests must stay green unchanged.

## Report

Changed files (exact list), test command + numbers per class, red-before evidence per fix
(one line each). Nothing else.
