package org.sterl.llmpeon.parts.config;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfigSaver;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.widgets.AgentModelConfigSection;
import org.sterl.llmpeon.parts.config.widgets.HorizontalRule;
import org.sterl.llmpeon.parts.config.widgets.TitledGroup;

/**
 * Advanced AI config page — per-agent overrides only (ADR-0063): the basic page is the single
 * owner of the base keys and the dev default slot; this page never writes them (one key, one
 * editor). Each override agent (po/plan/review/search/compact) gets one
 * {@link AgentModelConfigSection} composite — the base provider drives each section's think
 * widget form and extra-body visibility. The remaining base-level settings (timeout, max tokens,
 * query/header params, debug, realtime) stay as field editors.
 */
public class AiAdvancedPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public record AgentSection(String id, String title) {}

    public static final List<AgentSection> AGENT_SECTIONS = List.of(
            new AgentSection(AgentModelConfig.PO, "PO agent (Jon)"),
            new AgentSection(AgentModelConfig.PLAN, "Plan agent"),
            new AgentSection(AgentModelConfig.REVIEW, "Review agent"),
            new AgentSection(AgentModelConfig.SEARCH, "Search agent"),
            new AgentSection(AgentModelConfig.COMPACT, "Compact agent"));

    private final List<AgentModelConfigSection> sections = new ArrayList<>();

    public AiAdvancedPreferenceView() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PeonConstants.PLUGIN_ID));
        setDescription("Per-agent model selection and advanced AI settings.");
    }

    @Override
    public void createFieldEditors() {
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
        var config = LlmPreferenceInitializer.buildWithDefaults();
        // Tools field for every slot that can call tools — compact is excluded (D10:
        // CompactService builds its request without tools, an inert field would lie).
        var section = new AgentModelConfigSection(titledGroup.getGroup(), agentId,
                LlmPreferenceInitializer::buildWithDefaults, !AgentModelConfig.COMPACT.equals(agentId));
        section.load(config.modelConfigFor(agentId));
        section.loadTools(config.toolAllowlist(agentId));
        section.fetchModels();
        sections.add(section);
    }

    @Override
    public boolean performOk() {
        if (!super.performOk()) return false;
        // R-ATC-9: a bare '!' in any tools field rejects the WHOLE save — the store stays untouched.
        for (var section : sections) {
            if (!section.isToolsValid()) {
                showToolsError("The Tools field of \"" + titleOf(section.getAgentId()) + "\" contains a bare '!'. "
                        + "A '!' must be followed by a tool name or prefix (e.g. !debugJava*).");
                return false;
            }
        }
        var store = new EclipseLlmConfigStore(InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID));
        // Single owner (ADR-0063): only the per-agent slot keys — the base keys and the dev
        // default slot are written by the basic page alone.
        for (var section : sections) {
            LlmConfigSaver.saveAgentModelConfig(store, section.getAgentId(), section.getRecord());
            LlmConfigSaver.saveAgentTools(store, section.getAgentId(), section.getTools());
        }
        return true;
    }

    /** The R-ATC-9 validation failure dialog — the seam tests record instead of opening a modal. */
    protected void showToolsError(String message) {
        MessageDialog.openError(getShell(), "Invalid Tools value", message);
    }

    private static String titleOf(String agentId) {
        return AGENT_SECTIONS.stream()
                .filter(s -> s.id().equals(agentId))
                .map(AgentSection::title)
                .findFirst()
                .orElse(agentId);
    }

    @Override
    public void init(IWorkbench workbench) {}
}
