package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillPromptFile;
import org.sterl.llmpeon.skill.SkillService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * project-skills R18: the tool decides <b>when</b> to reload from disk, the
 * {@link SkillService} decides how ({@code refreshAll()}).
 */
public class SkillTool extends AbstractTool {

    public static final Duration DEFAULT_QUIET_PERIOD = Duration.ofMinutes(10);

    private final SkillService skillService;
    private final Duration quietPeriod;
    private final Clock clock;
    private volatile @Nullable Instant lastReload;

    /** R1 skill-evolution-loop: max ~2 lines, token discipline — ask is part of the read result. */
    private static final String USEFULNESS_FOOTER =
            "Report in your answer — helpful? wrong/outdated/incomplete? obsolete?";

    private record Lookup(@Nullable SkillPromptFile skill, @Nullable String warning) {}

    public SkillTool(SkillService skillService) {
        this(skillService, DEFAULT_QUIET_PERIOD, Clock.systemUTC());
    }

    public SkillTool(SkillService skillService, Duration quietPeriod, Clock clock) {
        super();
        if (quietPeriod.isNegative()) {
            throw new IllegalArgumentException("quietPeriod must not be negative: " + quietPeriod);
        }
        this.skillService = skillService;
        this.quietPeriod = quietPeriod;
        this.clock = clock;
    }

    @Tool("Read a skill's prompt by name. See skillList for available skills.")
    public String skillRead(@P(name = "name") String name) throws IOException, InterruptedException {
        ArgsUtil.requireNonBlank(name, "name");
        var lookup = lookup("skillRead", name);
        var skill = lookup.skill();
        if (skill == null) {
            return notFound(name, lookup.warning());
        }
        String result = withUsefulnessFooter(skill.renderBody());
        onTool("Reading SKILL 🧩 " + name + " " + StringUtil.charsSuffix(result));
        return withWarning(lookup.warning(), result);
    }

    @Tool("List all available skills with short descriptions. Reloads skills from disk — call it after a skill was created or changed, and before complex tasks.")
    public String skillList() throws IOException, InterruptedException {
        String warning = reload("skillList");
        List<SkillPromptFile> skills = skillService.getSkills();
        String result = skills.isEmpty() 
                ? "No skills available"
                : skills.stream().map(SkillPromptFile::buildShortInfo).collect(Collectors.joining("\n"));
        onTool("List SKILLs 🧩: " + skills.size() + " " + StringUtil.charsSuffix(result));
        return withWarning(warning, result);
    }

    @Tool("Read a file from a skill's directory by relative path. For templates and configs.")
    public String skillReadFile(@P(name = "name", description = "skill name") String name, @P(name = "path") String path) throws IOException {
        ArgsUtil.requireNonBlank(name, "name");
        ArgsUtil.requireNonBlank(path, "path");
        var lookup = lookup("skillReadFile", name);
        var skill = lookup.skill();
        if (skill == null) {
            return notFound(name, lookup.warning());
        }
        String content;
        try {
            content = skill.readRelativeFile(path);
        } catch (IllegalArgumentException e) {
            throw lookup.warning() == null ? e
                    : new IllegalArgumentException(withWarning(lookup.warning(), e.getMessage()), e);
        } catch (RuntimeException e) {
            throw lookup.warning() == null ? e
                    : new RuntimeException(withWarning(lookup.warning(), e.getMessage()), e);
        }
        String result = withUsefulnessFooter(content);
        onTool("Read file from SKILL " + name + ": " + path + " " + StringUtil.charsSuffix(result));
        return withWarning(lookup.warning(), result);
    }

    private Lookup lookup(String call, String name) {
        String warning = null;
        boolean reloaded = false;
        if (reloadDue()) {
            warning = reload(call);
            reloaded = true;
        }
        var skill = skillService.get(name).orElse(null);
        if (skill == null && !reloaded) {
            warning = reload(call);
            skill = skillService.get(name).orElse(null);
        }
        return new Lookup(skill, warning);
    }

    private String notFound(String name, @Nullable String warning) {
        onProblem("SKILL " + name + " not found ...");
        return withWarning(warning, "No skill with the name " + name
                + " found. Use one of: " + skillService.skillNames());
    }

    private boolean reloadDue() {
        var last = lastReload;
        return last == null || Duration.between(last, clock.instant()).compareTo(quietPeriod) >= 0;
    }

    /** @return {@code null} on success, else the warning line for the tool output. */
    private synchronized @Nullable String reload(String call) {
        try {
            skillService.refreshAll();
            lastReload = clock.instant();
            return null;
        } catch (IOException e) {
            // set only on success, reset on failure: the next call retries and warns again
            lastReload = null;
            var msg = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getName() : e.getMessage();
            onProblem("SKILL reload failed in " + call + ": " + msg);
            return "Skill reload failed: " + msg + " - showing last loaded state";
        }
    }

    private static String withWarning(@Nullable String warning, String result) {
        return warning == null ? result : warning + System.lineSeparator() + result;
    }

    private static String withUsefulnessFooter(String content) {
        return content + System.lineSeparator() + USEFULNESS_FOOTER;
    }
}
