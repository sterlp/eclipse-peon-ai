package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.ai.ReasoningPresets;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.widgets.HorizontalRule;
import org.sterl.llmpeon.parts.config.widgets.TitledGroup;

public class AiAdvancedPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

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

        addField(new StringFieldEditor(PeonConstants.PREF_MODEL,                  "Default Model (dev):", getFieldEditorParent()));
        addField(new DoubleSliderFieldEditor(PeonConstants.PREF_DEV_TEMPERATURE,  "Dev temperature:", getFieldEditorParent()));
        addField(new BooleanFieldEditor(PeonConstants.PREF_THINKING_ENABLED,      "Dev: Supports thinking", getFieldEditorParent()));

        addReasoningLegend();

        var thinkDevString = new TitledGroup(getFieldEditorParent(), "Dev/Default reasoning String");
        addField(new EditableComboFieldEditor(PeonConstants.PREF_THINK_ON_STRING,  "On string  (empty=auto):", ReasoningPresets.toArray(), thinkDevString.getGroup()));
        addField(new EditableComboFieldEditor(PeonConstants.PREF_THINK_OFF_STRING, "Off string (empty=nothing):", ReasoningPresets.toArray(), thinkDevString.getGroup()));

        addField(new StringFieldEditor(PeonConstants.PREF_PLAN_MODEL,             "Plan: Model (leave empty to use default):", getFieldEditorParent()));
        addField(new DoubleSliderFieldEditor(PeonConstants.PREF_PLAN_TEMPERATURE, "Plan temperature:", getFieldEditorParent()));
        addField(new BooleanFieldEditor(PeonConstants.PREF_PLAN_THINK_ENABLED,    "Plan: Supports thinking", getFieldEditorParent()));

        var thinkPlanString = new TitledGroup(getFieldEditorParent(), "Plan reasoning String");
        addField(new EditableComboFieldEditor(PeonConstants.PREF_PLAN_THINK_ON_STRING,  "On  (empty=auto):", ReasoningPresets.toArray(), thinkPlanString.getGroup()));
        addField(new EditableComboFieldEditor(PeonConstants.PREF_PLAN_THINK_OFF_STRING, "Off (empty=nothing):", ReasoningPresets.toArray(), thinkPlanString.getGroup()));

        addField(new StringFieldEditor(PeonConstants.PREF_SEARCH_MODEL,           "Search Model (leave empty to use default):", getFieldEditorParent()));
        addField(new StringFieldEditor(PeonConstants.PREF_COMPACT_MODEL,          "Compact Model (leave empty to use default):", getFieldEditorParent()));
        
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
        
        addField(new BooleanFieldEditor(PeonConstants.PREF_LOG_RESPONSE,          "Debug mode (logs requests & responses)", getFieldEditorParent()));
    }

    /**
     * One-line reminder of which reasoning values each provider expects. The on/off dropdowns accept
     * any typed value, so this just documents the common per-provider tokens.
     */
    private void addReasoningLegend() {
        Label legend = new Label(getFieldEditorParent(), SWT.WRAP);
        legend.setText("Reasoning value — OpenAI: high/medium/low/minimal · Claude: enabled/adaptive"
                + " · Ollama: true/false · LM Studio: any value (sent as \"reasoning\"). Empty = auto.");
        var gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
        gd.horizontalSpan = 2;
        gd.widthHint = 480;
        legend.setLayoutData(gd);
    }

    @Override
    public void init(IWorkbench workbench) {}
}
