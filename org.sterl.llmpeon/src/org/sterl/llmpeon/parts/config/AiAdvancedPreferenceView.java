package org.sterl.llmpeon.parts.config;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfigSaver;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.widgets.AgentModelConfigSection;
import org.sterl.llmpeon.parts.config.widgets.HorizontalRule;
import org.sterl.llmpeon.parts.config.widgets.ModelConfigWidget;
import org.sterl.llmpeon.parts.config.widgets.TitledGroup;

/**
 * Advanced AI config page. The dev slot is the default connection (ADR-0062): its section
 * (first, "Dev (Default)") mirrors the basic page's {@link ModelConfigWidget} + extra body and
 * writes the base keys on OK. The other agents (po/plan/search/compact) get one
 * {@link AgentModelConfigSection} composite each — the base provider drives each section's think
 * widget form and extra-body visibility. The remaining base-level settings (timeout, max tokens,
 * query/header params, debug, realtime) stay as field editors.
 */
public class AiAdvancedPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public record AgentSection(String id, String title) {}

    public static final List<AgentSection> AGENT_SECTIONS = List.of(
            new AgentSection(AgentModelConfig.DEV, "Dev (Default)"),
            new AgentSection(AgentModelConfig.PO, "PO agent (Jon)"),
            new AgentSection(AgentModelConfig.PLAN, "Plan agent"),
            new AgentSection(AgentModelConfig.SEARCH, "Search agent"),
            new AgentSection(AgentModelConfig.COMPACT, "Compact agent"));

    private final List<AgentModelConfigSection> sections = new ArrayList<>();
    private ModelConfigWidget devWidget;

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
        if (AgentModelConfig.DEV.equals(agentId)) {
            addDevSection(titledGroup);
            return;
        }
        var section = new AgentModelConfigSection(titledGroup.getGroup(), agentId, LlmPreferenceInitializer::buildWithDefaults);
        section.load(LlmPreferenceInitializer.buildWithDefaults().modelConfigFor(agentId));
        section.fetchModels();
        sections.add(section);
    }

    /**
     * The dev section (R-DEF-4/5/7): the base connection editor — the full basic-page
     * {@link ModelConfigWidget} (provider · url · key · model+refresh · think · temperature ·
     * ping) plus the extra body, right-aligned labels (advanced-page style).
     */
    private void addDevSection(TitledGroup titledGroup) {
        // The titled group is a single-column grid; the widget contract needs a 2-column grid.
        var grid = new Composite(titledGroup.getGroup(), SWT.NONE);
        grid.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        grid.setLayout(new GridLayout(2, false));

        var base = LlmPreferenceInitializer.buildWithDefaults();
        var devRecord = base.modelConfigFor(AgentModelConfig.DEV);
        devWidget = new ModelConfigWidget(grid, "dev", LlmPreferenceInitializer::buildWithDefaults, SWT.END);
        devWidget.load(new ModelConfigWidget.ConnectionValues(base.getProviderType(), base.getUrl(),
                base.getApiKey(), devRecord.think(), base.getModel(), devRecord.temperature(), devRecord.extraBody()));
        devWidget.fetchModels();
    }

    @Override
    public boolean performOk() {
        if (!super.performOk()) return false;
        var store = new EclipseLlmConfigStore(InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID));
        // Dev is the default slot (R-DEF-4): the widget writes the base keys — an empty url/key
        // removes the key (unset → provider default, R-DEF-3), never a null put. The saver then
        // writes llm.model + the dev think/temperature/extraBody; null url/key in the record
        // remove the legacy llm.agent.dev.url/apiKey overrides (ADR-0062 clean break).
        var values = devWidget.getValues();
        store.put(PeonConstants.PREF_PROVIDER_TYPE, values.provider().name());
        EclipseLlmConfigStore.putOrRemove(store, PeonConstants.PREF_URL, values.url());
        EclipseLlmConfigStore.putOrRemove(store, PeonConstants.PREF_API_KEY, values.apiKey());
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.DEV,
                new AgentModelConfig(null, null, values.model(), values.think(),
                        values.extraBody(), values.temperature()));
        for (var section : sections) {
            LlmConfigSaver.saveAgentModelConfig(store, section.getAgentId(), section.getRecord());
        }
        return true;
    }

    @Override
    public void init(IWorkbench workbench) {}
}
