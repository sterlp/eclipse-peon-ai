package org.sterl.llmpeon.parts.tools.memory;

import static org.sterl.llmpeon.parts.PeonConstants.PLUGIN_ID;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.sterl.llmpeon.StandingOrdersBuilder.MessageProvider;
import org.sterl.llmpeon.parts.tools.AbstractEclipseTool;
import org.sterl.llmpeon.shared.ArgsUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class WorkspaceMemoryTool extends AbstractEclipseTool implements MessageProvider {

    private static final String PREF_KEY = "workspaceGuidelineMemory";
    private static final int MAX_ENTRIES = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<WorkspaceGuideline>> LIST_TYPE = new TypeReference<>() {
    };

    private static WorkspaceMemoryTool INSTANCE;

    private final IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PLUGIN_ID);

    private final CopyOnWriteArrayList<WorkspaceGuideline> entries = new CopyOnWriteArrayList<>();

    public static WorkspaceMemoryTool getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WorkspaceMemoryTool();
        }
        return INSTANCE;
    }

    private WorkspaceMemoryTool() {
        load();
    }

    // ---------------------------------------------------------------------
    // Tools for the LLM
    // ---------------------------------------------------------------------

    @Tool("Store brief, important self-corrections or personal guidelines that help you avoid repeating mistakes.\n" +
          "For project-wide rules, conventions, or settings that all sessions should share, update AGENTS.md instead of using internal memory.\n" +
          "You have only " + MAX_ENTRIES + " memory slots, use them for highly reusable and important information.")
    public void addToMemory(
            @P(name = "text", description = "One short brief important rule, preference, or fact.") String text) {
        ArgsUtil.requireNonBlank(text, "text");

        var trimmed = text.trim();
        if (trimmed.isEmpty())
            return;

        synchronized (this) {
            if (entries.size() >= MAX_ENTRIES && !entries.isEmpty()) {
                entries.remove(0);
            }
            String date = LocalDate.now().toString();
            entries.add(new WorkspaceGuideline(trimmed, date));
            save();
        }
        onTool("Memorizing: " + text);
    }

    @Tool("Remove a guideline by its number as shown in the Memory block.")
    public void removeMemory(@P(name = "index", description = "1-based index from the Memory block.") Integer index) {

        ArgsUtil.requireNonNull(index, "index");

        int listIndex = index - 1;
        synchronized (this) {
            if (listIndex < 0 || listIndex >= entries.size())
                return;
            var forgot = entries.remove(listIndex);
            if (forgot != null) {
                onTool("Forgot: " + forgot.text());
                save();
            }
        }
    }

    @Tool("Replace the text of an existing guideline.")
    public void replaceMemory(@P(name = "index", description = "1-based index from the Memory block.") Integer index,
            @P(name = "text", description = "New short sentence for this guideline.") String text) {

        ArgsUtil.requireNonNull(index, "index");
        ArgsUtil.requireNonBlank(text, "text");

        int listIndex = index - 1;
        if (text == null || text.isBlank())
            return;

        synchronized (this) {
            if (listIndex < 0 || listIndex >= entries.size())
                return;
            WorkspaceGuideline old = entries.get(listIndex);
            entries.set(listIndex, new WorkspaceGuideline(text.trim(), old.createdAt()));
            save();
        }
        onTool("Memorizing: " + text);
    }

    @Tool("Clear all workspace guidelines. Use only if the user explicitly asks to reset memory.")
    public void resetMemory() {
        synchronized (this) {
            entries.clear();
            save();
        }
        onTool("Memory cleared");
    }

    // ---------------------------------------------------------------------
    // Storage helpers (Jackson)
    // ---------------------------------------------------------------------

    private void load() {
        String json = prefs.get(PREF_KEY, null);
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            List<WorkspaceGuideline> list = MAPPER.readValue(json, LIST_TYPE);
            entries.clear();
            entries.addAll(list);
        } catch (Exception e) {
            entries.clear();
        }
    }

    private void save() {
        try {
            String json = MAPPER.writeValueAsString(entries);
            prefs.put(PREF_KEY, json);
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String get() {
        if (entries.isEmpty()) return null;
        
        StringBuilder sb = new StringBuilder();
        sb.append("Your memory of rules and guidelines and informations for your work:\n\n");
        
        for (int i = 0; i < entries.size(); i++) {
            WorkspaceGuideline g = entries.get(i);
            int displayIndex = i + 1;
            sb.append(displayIndex).append(". [").append(g.createdAt()).append("] ").append(g.text()).append("\n");
        }
        
        return sb.toString().trim();
    }
}
