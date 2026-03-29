package org.sterl.llmpeon.template;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Processes VS Code-style {@code ${variable}} templates by substituting variables
 * from a context map. Unknown variables are left as-is. Null values become empty strings.
 *
 * @see <a href="https://peon-ai-4e.sterl.org/setup/template-variables/">Template Variables Documentation</a>
 */
public class TemplateContext {

    /**
     * Constant defining TODAY'S DATE in ISO format (yyyy-MM-dd).
     * Example value: {@code 2026-03-21}
     */
    public static final String CURRENT_DATE = "currentDate";

    /**
     * Constant defining WORKSPACE PATH as absolute normalized file-system path.
     * Example value: {@code /home/user/workspace}
     */
    public static final String WORK_PATH = "workPath";

    /**
     * Constant defining TOKEN SIZE limit (integer converted to string).
     * Used to configure token size limits for agent operations.
     * Example value: {@code 12}
     */
    public static final String TOKEN_SIZE = "tokenSize";

    /**
     * Constant defining TOKEN WINDOW maximum size (integer converted to string).
     * Maximum token count allowed for context processing in prompts.
     * Example value: {@code 20}
     */
    public static final String TOKEN_WINDOW = "tokenWindow";

    /**
     * Constant defining SKILL DIRECTORY path for agent/skill lookups.
     * Relative or absolute path to the skills directory.
     * Example value: {@code /workspace/skills}
     */
    public static final String SKILL_DIRECTORY = "skillDirectory";

    private final Map<String, String> ctx = new LinkedHashMap<String, String>();
    private Path workingDir;

    public TemplateContext(Path workingDir) {
        super();
        this.workingDir = workingDir;
    }
    
    public void setWorkingDir(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }
    
    public void setWorkingDir(String workingDir) {
        if (workingDir == null) return;
        setWorkingDir(Path.of(workingDir));
    }

    public Map<String, String> build() {
        ctx.put(CURRENT_DATE, LocalDate.now().toString());
        ctx.put(WORK_PATH, workingDir == null ? "" : workingDir.toAbsolutePath().normalize().toString());
        return ctx;
    }

    public String process(String template) {
        if (template == null || template.isEmpty()) return template;
        var variables = build();
        if (variables.isEmpty()) return template;

        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public void setTokenSize(int tokenSize) {
        ctx.put(TOKEN_SIZE, tokenSize + "");
    }

    public void setTokenWindow(int tokenWindow) {
        ctx.put(TOKEN_WINDOW, tokenWindow + "");
    }

    public void setSkillDirectory(String skillDirectory) {
        ctx.put(SKILL_DIRECTORY, skillDirectory);
    }
}