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

    /** Today's date in ISO format, e.g. {@code 2026-03-07} */
    public static final String CURRENT_DATE = "currentDate";
    public static final String WORK_PATH = "workPath";
    public static final String TOKEN_SIZE = "tokenSize";
    public static final String TOKEN_WINDOW = "tokenWindow";
    
    public static final String SKILL_DIRECTORY = "skillDirectory";
    
    private final Map<String, String> ctx = new LinkedHashMap<String, String>();
    private final Path workingDir;
    
    public TemplateContext(Path workingDir) {
        super();
        this.workingDir = workingDir;
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
