package org.sterl.llmpeon.parts.config;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigSaver;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.widgets.AgentModelConfigSection;
import org.sterl.llmpeon.parts.config.widgets.HorizontalRule;
import org.sterl.llmpeon.parts.config.widgets.TitledGroup;

/**
 * Advanced AI config page. The per-agent model config (url / key / model / think / temperature /
 * extra-body JSON) lives in five {@link AgentModelConfigSection} composites
 * (po/dev/plan/search/compact) — the base provider drives each section's think widget form and
 * extra-body visibility. The remaining base-level settings (timeout, max tokens, query/header
 * params, debug, realtime) stay as field editors.
 */
public class AiAdvancedPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public record AgentSection(String id, String title) {}

    public static final List<AgentSection> AGENT_SECTIONS = List.of(
            new AgentSection(AgentModelConfig.PO, "PO agent (Jon)"),
            new AgentSection(AgentModelConfig.DEV, "Dev agent (uses base model)"),
            new AgentSection(AgentModelConfig.PLAN, "Plan agent"),
            new AgentSection(AgentModelConfig.SEARCH, "Search agent"),
            new AgentSection(AgentModelConfig.COMPACT, "Compact agent"));

    private LlmConfig config;
    private final List<AgentModelConfigSection> sections = new ArrayList<>();

    public AiAdvancedPreferenceView() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PeonConstants.PLUGIN_ID));
        setDescription("Per-agent model selection and advanced AI settings.");
    }

    @Override
    public void createFieldEditors() {
        config = LlmPreferenceInitializer.buildWithDefaults();

        addField(new IntegerFieldEditor(PeonConstants.PREF_TIMEOUT, "Timeout in seconds (default 180s):",
                getFieldEditorParent()));

        new HorizontalRule(getFieldEditorParent());

        for (var section : AGENT_SECTIONS) {
            addAgentSection(section.id(), section.title());
        }

        new HorizontalRule(getFieldEditorParent());

        addField(new IntegerFieldEditor(PeonConstants.PREF_MAX_TOKENS,            "Max output tokens (0 to disable):", getFieldEditorParent()));

        var queryParamEditor = new StringFieldEditor(PeonConstants.PREF_QUERY_PARAMS,
                "Query Params (CSV: k=v,k2=v2):", getFieldEditorParent());
        queryParamEditor.setStringValue("");
        addField(queryParamEditor);

        var headerParamEditor = new StringFieldEditor(PeonConstants.PREF_HEADER_PARAMS,
                "Header Params (CSV: k=v,k2=v2):", getFieldEditorParent());
        headerParamEditor.setStringValue("");
        addField(headerParamEditor);

        addField(new BooleanFieldEditor(PeonConstants.PREF_LOG_RESPONSE,          "Debug mode (logs requests/responses and internals)", getFieldEditorParent()));
        addField(new BooleanFieldEditor(PeonConstants.PREF_SHOW_REALTIME_AI_RESPONSE, "Show real-time AI response in chat", getFieldEditorParent()));
    }

    private void addAgentSection(String agentId, String title) {
        var titledGroup = new TitledGroup(getFieldEditorParent(), title);
        var section = new AgentModelConfigSection(titledGroup.getGroup(), agentId, config);
        section.load(config.modelConfigFor(agentId));
        section.fetchModels();
        sections.add(section);
    }

    @Override
    public boolean performOk() {
        if (!super.performOk()) return false;
        var store = new EclipseLlmConfigStore(InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID));
        for (var section : sections) {
            LlmConfigSaver.saveAgentModelConfig(store, section.getAgentId(), section.getRecord());
        }
        return true;
    }

    @Override
    public void init(IWorkbench workbench) {}
}
