# Template Variables Documentation Update Plan

## Context

The `TemplateContext.java` source code and the `template-variables.md` documentation file contain inconsistencies that need to be addressed to ensure accurate, up-to-date documentation for users.

## Affected Files

### Source Code
- `/llmpeon-parent/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/template/TemplateContext.java`

### Documentation
- `/llmpeon-parent/doc/docs/setup/template-variables.md`

## Discrepancies Found

| Issue | Current State | Required Fix |
|-------|---------------|---------------|
| **Example value formatting** | `12.000`, `20.000` (with thousand separators) | `12`, `20` (integers converted to strings) |
| **Missing javadoc comments** | Only `CURRENT_DATE` has javadoc; others lack explanation | Add javadoc comments explaining purpose and format for all constants |
| **Description clarity (tokenSize)** | "Current used token count" (ambiguous terminology) | Clarify: "Current configured token size limit (integer)" |
| **Description clarity (tokenWindow)** | "Maximum token size configured - maximum amount of token to use" (redundant phrasing) | Simplify to: "Maximum token window size for context processing" |
| **GitHub link** | Points to `github.com/sterlp/eclipse-peon-ai` | Update to correct workspace-relative path or remove if external source not maintained |

## Step-by-Step Changes

### 1. Update `TemplateContext.java` - Add Javadoc Comments

Add inline javadoc comments for all constants to match the documentation intent:

```java
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
```

### 2. Update `template-variables.md` - Fix Example Values and Descriptions

#### Table Updates

Change from:
```markdown
| `${tokenSize}` | Current used token count | `12.000` |
| `${tokenWindow}` | Maximum token size configured - maximum amount of token to use | `20.000` |
```

To:
```markdown
| `${tokenSize}` | Current configured token size limit (integer) | `12` |
| `${tokenWindow}` | Maximum token window size for context processing (integer) | `20` |
```

#### Javadoc Link Update

Change the GitHub link to use a relative path or update if an external source is maintained:

From:
```markdown
All template variables are provided by [`TemplateContext.java`](https://github.com/sterlp/eclipse-peon-ai/blob/main/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/template/TemplateContext.java).
```

To (if external repo not maintained):
```markdown
All template variables are provided by [`TemplateContext.java`](../org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/template/TemplateContext.java).
```

### 3. Verify Consistency

After updates, verify:
- All constants have matching javadoc in `TemplateContext.java`
- Documentation examples match the actual Java string formatting (integers -> strings)
- Descriptions are clear and non-redundant
- Cross-links between files work correctly

## Verification Steps

1. Run `mvn clean package` to ensure no build errors after javadoc additions
2. Review compiled documentation for clarity and accuracy
3. Confirm template substitution behavior matches documented examples

## Open Questions

1. **Token terminology**: Should "token size" vs "token window" be clarified further in the user-facing documentation to avoid confusion with model-specific token concepts?
2. **GitHub repo URL**: Is the external GitHub link (`github.com/sterlp/eclipse-peon-ai`) an active source of truth, or should documentation point only to workspace-relative paths?
3. **Example values**: Are `12` and `20` representative example values, or should they be replaced with more realistic/useful examples from the actual codebase?
