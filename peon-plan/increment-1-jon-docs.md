# Increment 1 — Peon-PO (Jon) writes docs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ship a selectable **Peon-PO / Jon** agent that reads and writes **only** under `docs/`
(`*/docs/*` + `*.md`) using the IST/SOLL/WEIL + GIVEN/WHEN/THEN docs methodology.

**Architecture:** A per-agent `WriteValidator` is provided by the agent per request (exactly like
`getToolFilter()`), carried on `ToolLoopRequest`, and enforced at each write tool's path entry via a
shared `AbstractTool.validateWrite(path)` helper. Only `AiPoAgent` overrides the validator to `DOCS`;
every other agent inherits `ALLOW_ALL`. Jon lives in **core** (unit-tested with the disk write tool);
the Eclipse plugin gives Jon his own curated `ToolService` of Eclipse read/write/grep tools.

**Tech Stack:** Java (same level as the module today — `var`, records), JUnit 5 + AssertJ, langchain4j
tool annotations, Lombok `@Builder` on `ToolLoopRequest`, Eclipse RCP for the plugin layer.

## Global Constraints

- **No commits.** The user commits manually — never `git commit`/`push`. Each task ends at a green-tests
  checkpoint, left uncommitted. (Standing user rule.)
- **core stays headless.** No Eclipse (`org.eclipse.*`) imports in `org.sterl.llmpeon.core`.
- **KV-cache safety.** Tool sets are static per agent instance; do not add/remove tools per turn.
- **Test model helper:** `LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build()`
  returns a `ConfiguredChatModel` usable in unit tests (no server needed for construction-only tests).
- **Glob semantics:** only `*` is special (→ `.*`, crosses `/`), everything else literal, anchored,
  case-insensitive; matched against the **raw path string** the model supplied.
- **Backward compatibility:** existing write-tool callers pass no `ToolLoopRequest`; the validator check
  must be a no-op when `request == null`.

**Spec:** [docs/write-path-validator.md](../docs/write-path-validator.md) (R1–R4 + BDD),
[ADR-0022](../docs/adr/0022-write-path-allowlist-decorator.md), story [docs/po-agent-jon.md](../docs/po-agent-jon.md).

---

### Task 1: Glob → cached Pattern in `RegexUtils`

**Files:**
- Modify: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/shared/RegexUtils.java`
- Test: `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/shared/RegexUtilsTest.java`

**Interfaces:**
- Produces: `RegexUtils.globToPattern(String glob) -> java.util.regex.Pattern` (cached, anchored,
  case-insensitive; `*` → `.*`, rest literal).

- [ ] **Step 1: Write the failing tests** (append to `RegexUtilsTest`)

```java
    @Test
    void globToPattern_matchesDocsAtDepth() {
        var p = RegexUtils.globToPattern("*/docs/*");
        assertTrue(p.matcher("MyProject/docs/feature.md").matches());
        assertTrue(p.matcher("a/b/docs/x/y.md").matches());
        assertFalse(p.matcher("src/main/Foo.java").matches());
    }

    @Test
    void globToPattern_matchesMarkdownAnywhere() {
        var p = RegexUtils.globToPattern("*.md");
        assertTrue(p.matcher("docs/feature.md").matches());
        assertTrue(p.matcher("README.md").matches());
        assertFalse(p.matcher("docs/notes.txt").matches());
    }

    @Test
    void globToPattern_isCachedPerGlob() {
        assertSame(RegexUtils.globToPattern("*.md"), RegexUtils.globToPattern("*.md"));
    }
```

Add the imports `import static org.junit.jupiter.api.Assertions.assertSame;` (assertTrue/assertFalse
are already imported in this file).

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=RegexUtilsTest`
Expected: FAIL — `globToPattern` does not exist.

- [ ] **Step 3: Implement `globToPattern`** (add to `RegexUtils`)

```java
    private static final java.util.Map<String, Pattern> GLOB_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Compiles a simple glob into a cached, anchored, case-insensitive {@link Pattern}. Only {@code *}
     * is special — it matches any run of characters including {@code /}; every other character is
     * matched literally. The pattern is cached per glob string, so repeated calls reuse the instance.
     */
    public static Pattern globToPattern(String glob) {
        return GLOB_CACHE.computeIfAbsent(glob, RegexUtils::compileGlob);
    }

    private static Pattern compileGlob(String glob) {
        String[] parts = glob.split("\\*", -1);
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(".*");
            if (!parts[i].isEmpty()) sb.append(Pattern.quote(parts[i]));
        }
        sb.append("$");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=RegexUtilsTest`
Expected: PASS (all, including the pre-existing tests).

- [ ] **Step 5: Checkpoint** — tests green; leave uncommitted.

---

### Task 2: `WriteValidator` + `AllowlistWriteValidator`

**Files:**
- Create: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/WriteValidator.java`
- Create: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/AllowlistWriteValidator.java`
- Test: `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/tool/AllowlistWriteValidatorTest.java`

**Interfaces:**
- Consumes: `RegexUtils.globToPattern(String)` (Task 1).
- Produces:
  - `interface WriteValidator { void validate(String path); }` with `WriteValidator.ALLOW_ALL` and
    `WriteValidator.DOCS` constants.
  - `class AllowlistWriteValidator implements WriteValidator` with `AllowlistWriteValidator(String... globs)`.

- [ ] **Step 1: Write the failing test**

```java
package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AllowlistWriteValidatorTest {

    final WriteValidator docs = WriteValidator.DOCS;

    @Test
    void allows_markdown_in_docs() {
        assertDoesNotThrow(() -> docs.validate("MyProject/docs/feature.md"));
        assertDoesNotThrow(() -> docs.validate("docs/feature.md")); // via *.md
    }

    @Test
    void allows_any_markdown_anywhere() {
        assertDoesNotThrow(() -> docs.validate("README.md"));
    }

    @Test
    void allows_non_markdown_inside_a_docs_path() {
        assertDoesNotThrow(() -> docs.validate("proj/docs/img/logo.png"));
    }

    @Test
    void rejects_source_file() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> docs.validate("src/main/java/Foo.java"));
        assertTrue(ex.getMessage().contains("Write denied"));
    }

    @Test
    void allowAll_allows_everything() {
        assertDoesNotThrow(() -> WriteValidator.ALLOW_ALL.validate("anything/at/all.bin"));
    }
}
```

- [ ] **Step 2: Run — verify it fails**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=AllowlistWriteValidatorTest`
Expected: FAIL — `WriteValidator` does not exist.

- [ ] **Step 3: Implement `WriteValidator`**

```java
package org.sterl.llmpeon.tool;

/**
 * Vets the raw path an agent's model passes to a write tool. Provided per request by the agent
 * (see {@code AiAgent.getWriteValidator()}) and enforced at the write tool's path entry.
 */
public interface WriteValidator {

    /**
     * @param path the raw path string the model supplied to the write tool
     * @throws IllegalArgumentException if this agent may not write to that path
     */
    void validate(String path);

    /** No restriction — the default for every agent except Jon. */
    WriteValidator ALLOW_ALL = path -> { /* everything is allowed */ };

    /** Jon's scope: a docs folder at any depth, plus any Markdown file. */
    WriteValidator DOCS = new AllowlistWriteValidator("*/docs/*", "*.md");
}
```

- [ ] **Step 4: Implement `AllowlistWriteValidator`**

```java
package org.sterl.llmpeon.tool;

import java.util.List;

import org.sterl.llmpeon.shared.RegexUtils;

public class AllowlistWriteValidator implements WriteValidator {

    private final List<String> globs;

    public AllowlistWriteValidator(String... globs) {
        this.globs = List.of(globs);
    }

    @Override
    public void validate(String path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        for (String glob : globs) {
            if (RegexUtils.globToPattern(glob).matcher(path).matches()) return;
        }
        throw new IllegalArgumentException(
                "Write denied: '" + path + "' is outside this agent's allowed paths " + globs
                + ". You may only write to " + globs + ".");
    }
}
```

- [ ] **Step 5: Run — verify it passes**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=AllowlistWriteValidatorTest`
Expected: PASS.

- [ ] **Step 6: Checkpoint** — tests green; leave uncommitted.

---

### Task 3: Plumb the validator through the request chain

**Files:**
- Modify: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/ToolLoopRequest.java`
- Modify: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AiAgent.java`
- Modify: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AbstractAgent.java:225-236`
- Modify: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/AbstractTool.java`
- Test: `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/tool/ToolServiceTest.java` (append)

**Interfaces:**
- Consumes: `WriteValidator` (Task 2).
- Produces:
  - `ToolLoopRequest.getWriteValidator() -> WriteValidator` (builder field `writeValidator`, default
    `ALLOW_ALL`).
  - `AiAgent.getWriteValidator() -> WriteValidator` (default `ALLOW_ALL`).
  - `AbstractTool.validateWrite(String path)` (protected; no-op when `request == null`).

- [ ] **Step 1: Write the failing test** (append to `ToolServiceTest`)

```java
    @org.junit.jupiter.api.Test
    void toolLoopRequest_defaultsToAllowAllValidator() {
        var model = org.sterl.llmpeon.ai.LlmConfig
                .newConfig(org.sterl.llmpeon.ai.AiProvider.OLLAMA, "test-model", "http://localhost:9999")
                .build();
        var req = org.sterl.llmpeon.tool.ToolLoopRequest.builder()
                .memory(new org.sterl.llmpeon.memory.ThreadSafeMemory())
                .chatModel(model)
                .build();
        org.junit.jupiter.api.Assertions.assertSame(
                org.sterl.llmpeon.tool.WriteValidator.ALLOW_ALL, req.getWriteValidator());
    }
```

- [ ] **Step 2: Run — verify it fails**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=ToolServiceTest#toolLoopRequest_defaultsToAllowAllValidator`
Expected: FAIL — `getWriteValidator` does not exist.

- [ ] **Step 3: Add the field to `ToolLoopRequest`** (after the `toolNameFilter` field, ~line 61; same
package `org.sterl.llmpeon.tool`, so no import needed)

```java
    /**
     * Per-agent write-path validator. Set by every agent via {@code AiAgent.getWriteValidator()}.
     * Default: allow all — only Peon-PO (Jon) restricts it.
     */
    @Default
    @Getter
    public WriteValidator writeValidator = WriteValidator.ALLOW_ALL;
```

- [ ] **Step 4: Add the default to `AiAgent`** (add import `org.sterl.llmpeon.tool.WriteValidator`)

```java
    /**
     * The write-path validator this agent applies to every write tool call. Default: no restriction.
     * Peon-PO (Jon) overrides this to scope writes to docs. Provided per request, like the tool filter.
     */
    default WriteValidator getWriteValidator() {
        return WriteValidator.ALLOW_ALL;
    }
```

- [ ] **Step 5: Wire it into `AbstractAgent.doCall`** — in the `ToolLoopRequest.builder()` chain
(currently ends `...standingOrders(standingOrders).build()`, lines 226-235), add one line:

```java
                    .toolNameFilter(getToolNameFilter())
                    .writeValidator(getWriteValidator())
                    .agentConfig(getConfig())
```

- [ ] **Step 6: Add the `validateWrite` helper to `AbstractTool`**

```java
    /**
     * Enforce the current request's {@link org.sterl.llmpeon.tool.WriteValidator} on a raw write path.
     * No-op when the tool is invoked without a request (e.g. direct unit-test calls).
     *
     * @throws IllegalArgumentException if the agent may not write to {@code path}
     */
    protected void validateWrite(String path) {
        if (request != null) request.getWriteValidator().validate(path);
    }
```

- [ ] **Step 7: Run — verify it passes**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=ToolServiceTest`
Expected: PASS.

- [ ] **Step 8: Checkpoint** — tests green; leave uncommitted.

---

### Task 4: Enforce the validator in `DiskFileWriteTool`

**Files:**
- Modify: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/DiskFileWriteTool.java:202-204`
- Test: `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/tool/DiskFileWriteToolTest.java` (append)

**Interfaces:**
- Consumes: `AbstractTool.validateWrite(String)` (Task 3), `WriteValidator.DOCS` (Task 2),
  `ToolLoopRequest` builder (Task 3).

- [ ] **Step 1: Write the failing tests** (append to `DiskFileWriteToolTest`; add imports
  `org.sterl.llmpeon.memory.ThreadSafeMemory`, `org.sterl.llmpeon.ai.LlmConfig`,
  `org.sterl.llmpeon.ai.AiProvider`, `org.sterl.llmpeon.tool.ToolLoopRequest`,
  `org.sterl.llmpeon.tool.WriteValidator`)

```java
    private ToolLoopRequest docsRequest() {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .writeValidator(WriteValidator.DOCS)
                .build();
    }

    @Test
    void write_allowedInsideDocs() {
        tool.withToolRequest(docsRequest());
        tool.diskWriteFile("proj/docs/feature.md", "hello");
        assertTrue(Files.exists(tempDir.resolve("proj/docs/feature.md")));
    }

    @Test
    void write_rejectedOutsideDocs() {
        tool.withToolRequest(docsRequest());
        assertThrows(IllegalArgumentException.class,
                () -> tool.diskWriteFile("src/main/java/Foo.java", "x"));
        assertFalse(Files.exists(tempDir.resolve("src/main/java/Foo.java")));
    }

    @Test
    void write_withoutRequest_isUnrestricted() {
        tool.diskWriteFile("anywhere/file.txt", "x"); // no withToolRequest -> request == null
        assertTrue(Files.exists(tempDir.resolve("anywhere/file.txt")));
    }
```

- [ ] **Step 2: Run — verify the first two fail**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=DiskFileWriteToolTest`
Expected: FAIL — `write_rejectedOutsideDocs` writes the file (no enforcement yet).

- [ ] **Step 3: Enforce in `resolve`** — change the private `resolve` (lines 202-204) to:

```java
    private Path resolve(String path) {
        validateWrite(path);
        return FileUtils.resolve(workingDir, path);
    }
```

- [ ] **Step 4: Run — verify all pass**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=DiskFileWriteToolTest`
Expected: PASS (new + all pre-existing tests, which pass no request).

- [ ] **Step 5: Checkpoint** — tests green; leave uncommitted.

---

### Task 5: `AiPoAgent` + `po.txt` system prompt (core)

**Files:**
- Create: `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AiPoAgent.java`
- Create: `org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/po.txt`
- Test: `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/agent/AiPoAgentTest.java`

**Interfaces:**
- Consumes: `AbstractAgent`, `PromptLoader.loadWithDefault`, `WriteValidator.DOCS`,
  `AiDevAgent(ConfiguredChatModel, ToolService)` (for the default-validator assertion).
- Produces: `AiPoAgent.NAME = "Peon-PO"`; constructors `(ConfiguredChatModel, ToolService)` and
  `(ConfiguredChatModel, ToolService, Path historyConfigDir)`; `getWriteValidator() == DOCS`.

- [ ] **Step 1: Write the failing test**

```java
package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;

class AiPoAgentTest {

    private AiPoAgent newAgent() {
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999");
        return new AiPoAgent(config.build(), new ToolService());
    }

    @Test
    void name_isPeonPO() {
        assertEquals("Peon-PO", newAgent().getName());
    }

    @Test
    void writeValidator_isDocs() {
        assertSame(WriteValidator.DOCS, newAgent().getWriteValidator());
    }

    @Test
    void systemPrompt_carriesTheMethodology() {
        var p = newAgent().getSystemPrompt();
        assertThat(p).contains("IST").contains("SOLL").contains("WEIL");
        assertThat(p).contains("GIVEN").contains("WHEN").contains("THEN");
        assertThat(p).contains("docs/");
    }

    @Test
    void otherAgents_defaultToAllowAll() {
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "m", "http://localhost:9999");
        assertSame(WriteValidator.ALLOW_ALL,
                new AiDevAgent(config.build(), new ToolService()).getWriteValidator());
    }
}
```

- [ ] **Step 2: Run — verify it fails**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=AiPoAgentTest`
Expected: FAIL — `AiPoAgent` does not exist.

- [ ] **Step 3: Create `po.txt`** (methodology prompt; will be tuned with the user's prompts at the end
  of the increment — the assertions above pin the required keywords)

```
You are Jon, Peon-PO — a skeptical business-owner and guardian of docs/.

Your job is to design features WITH the user and record them under docs/. You do not write code and you
may only write files under docs/ (allowed paths: */docs/* and *.md). Reads are unrestricted.

How you work:
- Propose every change as IST / SOLL / WEIL (current state / target state / why).
- When a behaviour is unclear, sharpen it as a rule in GIVEN / WHEN / THEN and ask the user to confirm
  or correct it — one open question at a time (KISS).
- Structure docs as: a Feature is one technical component / package / service → one docs/<feature>.md
  file. A Feature contains Use Cases (== Acceptance Criteria == Anwendungsfälle). A Use Case contains
  Rules. Each Rule carries its BDD tests as GIVEN / WHEN / THEN.
- Register new feature docs in docs/index.md; technical decisions go to docs/adr/ as ADRs.
- At the end of a piece of work: review what you wrote, back it into the docs, and compress the docs
  carefully (conservatively) — never drop a rule, a rationale, or a file reference.

Be concise. Prefer the smallest change that satisfies the user. Ground every claim in the actual docs.
```

- [ ] **Step 4: Create `AiPoAgent`**

```java
package org.sterl.llmpeon.agent;

import java.nio.file.Path;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;

/**
 * Peon-PO ("Jon") — a docs-owning agent. Reads freely, writes only under docs/ (via {@link
 * WriteValidator#DOCS}). Mirrors {@link AiPlanAgent} for model/temperature/config.
 */
public class AiPoAgent extends AbstractAgent {

    public static final String NAME = "Peon-PO";
    private static final String BASE_PROMPT = PromptLoader.loadWithDefault("po.txt");

    public AiPoAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
        super(configuredModel, toolService);
    }

    public AiPoAgent(ConfiguredChatModel configuredModel, ToolService toolService, Path historyConfigDir) {
        super(configuredModel, toolService,
                historyConfigDir == null ? new ThreadSafeMemory()
                        : new ThreadSafeMemory(new FileAgentHistoryStore(historyFile(historyConfigDir, NAME))));
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getSystemPrompt() {
        return BASE_PROMPT;
    }

    @Override
    public WriteValidator getWriteValidator() {
        return WriteValidator.DOCS;
    }

    @Override
    public Double getTemperature() {
        return configuredModel.getConfig().getPlanTemperature();
    }

    @Override
    public AgentConfig getConfig() {
        return configuredModel.getConfig().planAgentConfig();
    }

    @Override
    public boolean isThinkSupported() {
        return configuredModel.getConfig().isPlanThinkSupported();
    }
}
```

- [ ] **Step 5: Run — verify it passes**

Run: `mvn -pl org.sterl.llmpeon.core -am test -Dtest=AiPoAgentTest`
Expected: PASS.

- [ ] **Step 6: Checkpoint** — tests green; leave uncommitted.

---

### Task 6: Enforce the validator in `EclipseWorkspaceWriteFileTool` (plugin)

**Files:**
- Verify/Modify: `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/AbstractEclipseTool.java`
  (must extend `AbstractTool` so `validateWrite` + `request` are available; it already uses
  `monitor`/`onTool`, so it does — confirm the `extends AbstractTool`).
- Modify: `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/tools/EclipseWorkspaceWriteFileTool.java`
- Test (best-effort, Eclipse test module): `org.sterl.llmpeon.test/.../EclipseWorkspaceWriteValidatorTest`

**Interfaces:**
- Consumes: `AbstractTool.validateWrite(String)` (Task 3).

- [ ] **Step 1: Confirm the base class** — open `AbstractEclipseTool.java` and verify it
  `extends org.sterl.llmpeon.tool.tools.AbstractTool`. If it does not, change it to extend it (it
  already relies on `AbstractTool`'s `monitor`/`request`/`withToolRequest`), keeping its own members.

- [ ] **Step 2: Add `validateWrite` at the top of each write method** of
  `EclipseWorkspaceWriteFileTool`, immediately after the existing `ArgsUtil.requireNonBlank(filePath,...)`
  guard and **before** `EclipseUtil.resolveInEclipse(...)`. Concretely:
  - `eclipseReplaceLines` → `validateWrite(filePath);`
  - `eclipseEditFile` → `validateWrite(filePath);`
  - `eclipseWriteFile` → `validateWrite(filePath);`
  - `eclipseInsertLines` → `validateWrite(filePath);`
  - `eclipseDeleteResource` → `validateWrite(filePath);`
  - `eclipseRenameResource` → `validateWrite(sourcePath); validateWrite(targetPath);`

  Example (`eclipseWriteFile`, after line 90 `ArgsUtil.requireNonNull(content, "content");`):

```java
        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(content, "content");
        validateWrite(filePath);
```

- [ ] **Step 3: Best-effort rejection test** (in the Eclipse test module `org.sterl.llmpeon.test`).
  The check throws **before** any Eclipse API is touched, so a denied path needs no workspace:

```java
    @org.junit.jupiter.api.Test
    void eclipseWrite_rejectsOutsideDocs() {
        var tool = new EclipseWorkspaceWriteFileTool();
        var model = org.sterl.llmpeon.ai.LlmConfig
                .newConfig(org.sterl.llmpeon.ai.AiProvider.OLLAMA, "m", "http://localhost:9999").build();
        tool.withToolRequest(org.sterl.llmpeon.tool.ToolLoopRequest.builder()
                .memory(new org.sterl.llmpeon.memory.ThreadSafeMemory())
                .chatModel(model)
                .writeValidator(org.sterl.llmpeon.tool.WriteValidator.DOCS)
                .build());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> tool.eclipseWriteFile("MyProject/src/Foo.java", "x"));
    }
```

  If class-loading in the test harness makes this impractical, drop the test and rely on the manual
  verification in Task 7 — note that in the plan checkbox rather than leaving it silently untested.

- [ ] **Step 4: Run the module test** (or note the manual fallback)

Run: `mvn -pl org.sterl.llmpeon.test -am verify -Dtest=EclipseWorkspaceWriteValidatorTest`
Expected: PASS (or documented manual fallback).

- [ ] **Step 5: Checkpoint** — green (or fallback noted); leave uncommitted.

---

### Task 7: Register Jon with his own curated ToolService (plugin)

**Files:**
- Modify: `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonAiService.java:114-143`

**Interfaces:**
- Consumes: `AiPoAgent(ConfiguredChatModel, ToolService, Path)` (Task 5), the existing
  `workspaceWriteFilesTool` instance, `EclipseWorkspaceReadFileTool`, `EclipseGrepTool`,
  `agentService.addPersistentAgent(...)`.

- [ ] **Step 1: Capture the read + grep tool instances as fields.** Currently they are added inline
  (`sharedToolService.addTool(new EclipseWorkspaceReadFileTool());` at :116 and
  `sharedToolService.addTool(new EclipseGrepTool());` at :125). Change to:

```java
        workspaceReadFilesTool = new EclipseWorkspaceReadFileTool();
        sharedToolService.addTool(workspaceReadFilesTool);
        ...
        eclipseGrepTool = new EclipseGrepTool();
        sharedToolService.addTool(eclipseGrepTool);
```

  and declare the fields next to the existing `workspaceWriteFilesTool` field:

```java
    private EclipseWorkspaceReadFileTool workspaceReadFilesTool;
    private EclipseGrepTool eclipseGrepTool;
```

- [ ] **Step 2: Build Jon's curated ToolService and register him** — after the scaffold registration
  (`agentService.addPersistentAgent(scaffoldAgent);`, :143), add:

```java
        // Peon-PO (Jon): own curated tool set — docs read/write/grep only (no shell/build/test/plan).
        var poToolService = new ToolService(false);
        poToolService.addTool(workspaceReadFilesTool);
        poToolService.addTool(workspaceWriteFilesTool); // same instance -> project + validator per request
        poToolService.addTool(eclipseGrepTool);
        var poAgent = new AiPoAgent(configuredModel, poToolService, config.getConfigDir());
        agentService.addPersistentAgent(poAgent);
```

  Add the import `import org.sterl.llmpeon.agent.AiPoAgent;`.

- [ ] **Step 3: Compile the plugin**

Run: `mvn -pl org.sterl.llmpeon -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Manual verification** (this is the increment's testable feature). Launch the Eclipse
  runtime, then:
  - The agent dropdown lists **Peon-PO**. Select it.
  - Ask Jon to create `docs/increment-1-smoke.md` with a line of content → the file appears in the
    Project Explorer / opens; content is correct.
  - Ask Jon to write `src/Smoke.java` → he is refused with a "Write denied" message (visible as an
    `onProblem`), and no file is created.
  - Ask Jon to read an existing source file (e.g. `AbstractAgent.java`) → allowed (reads are not gated).

- [ ] **Step 5: Checkpoint** — plugin compiles, manual checks pass; leave uncommitted.

---

### Task 8 (optional): First-activation tutorial for Jon

Deliverable: a one-time greeting when Jon is first selected (like Peon-Scaffold). Skippable — the core
feature works without it. Implement only if desired this increment.

**Files:**
- Create: `org.sterl.llmpeon.core/src/main/resources/org/sterl/llmpeon/prompts/po-tutorial.txt`
- Modify: `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonAiService.java` (`getScaffoldTutorial`, :388-394)
- Modify: `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java:559-562` (only if the method is
  renamed; otherwise no change)

- [ ] **Step 1: Create `po-tutorial.txt`**

```
Hi, I'm Jon (Peon-PO). I design features with you and keep docs/ tidy.
Tell me a feature you want, and I'll draft it as IST/SOLL/WEIL and sharpen the rules with you in
GIVEN/WHEN/THEN. I only write under docs/.
```

- [ ] **Step 2: Generalise the tutorial lookup** — in `PeonAiService`, change `getScaffoldTutorial()` so
  it also returns Jon's tutorial on his first activation:

```java
    public String getScaffoldTutorial() {
        var agent = getActiveAgent();
        if (agent == null || agent.getMemory().size() != 0) return null;
        if (agent instanceof AiScaffoldAgent) return PromptLoader.load("scaffold-tutorial.txt");
        if (agent instanceof AiPoAgent)       return PromptLoader.load("po-tutorial.txt");
        return null;
    }
```

  (`AIChatView` already renders whatever this returns; no change needed there.)

- [ ] **Step 3: Manual verification** — first selection of Peon-PO shows the greeting; second selection
  (memory non-empty) does not.

- [ ] **Step 4: Checkpoint** — leave uncommitted.

---

## Increment close-out (Jon's own ritual, done by us here)

- [ ] Run the whole core module: `mvn -pl org.sterl.llmpeon.core -am test` → all green.
- [ ] Manual Eclipse smoke (Task 7 Step 4) passes.
- [ ] **Prompt tuning:** replace/extend `po.txt` with the user's supplied prompts; keep the asserted
  keywords (IST/SOLL/WEIL, GIVEN/WHEN/THEN, docs/) so `AiPoAgentTest` stays green.
- [ ] **Docs backdrop + careful compression:** re-read `docs/write-path-validator.md`, `docs/index.md`,
  `docs/adr/0022-...md`, `docs/po-agent-jon.md`; fold in anything learned; compress conservatively
  (never drop a rule, rationale, or file reference). Mark Jon's increment-1 slice as done in
  `peon-plan/po-agent-jon-overview.md`.
- [ ] Leave everything uncommitted for the user to review and commit.

## Self-Review (against the spec)

- **R1 (validate before write, allow docs / reject src):** Task 4 (disk) + Task 6 (eclipse). ✅
- **R2 (default allow-all):** Task 3 (`AiAgent` default) + `AiPoAgentTest.otherAgents_defaultToAllowAll`. ✅
- **R3 (reads never gated):** only `validateWrite` is added, only in write tools; read tools untouched —
  asserted implicitly by Task 7 manual read check. ✅
- **R4 (compiled once, cached):** Task 1 `globToPattern` cache + `globToPattern_isCachedPerGlob`. ✅
- **Selectable Jon reads/writes docs:** Task 5 (agent) + Task 7 (registration + Eclipse tools). ✅
- Type consistency: `WriteValidator`, `getWriteValidator()`, `validateWrite(String)`, `AiPoAgent.NAME`,
  `globToPattern(String)` used identically across tasks. ✅
