package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
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
        
        var thinkDevString = new TitledGroup(getFieldEditorParent(), "Dev/Default reasoning String");
        addField(new StringFieldEditor(PeonConstants.PREF_THINK_ON_STRING,        "On string  (empty=auto):", thinkDevString.getGroup()));
        addField(new StringFieldEditor(PeonConstants.PREF_THINK_OFF_STRING,       "Off string (empty=nothing):", thinkDevString.getGroup()));
        
        addField(new StringFieldEditor(PeonConstants.PREF_PLAN_MODEL,             "Plan: Model (leave empty to use default):", getFieldEditorParent()));
        addField(new DoubleSliderFieldEditor(PeonConstants.PREF_PLAN_TEMPERATURE, "Plan temperature:", getFieldEditorParent()));
        addField(new BooleanFieldEditor(PeonConstants.PREF_PLAN_THINK_ENABLED,    "Plan: Supports thinking", getFieldEditorParent()));
        
        var thinkPlanString = new TitledGroup(getFieldEditorParent(), "Plan reasoning String");
        addField(new StringFieldEditor(PeonConstants.PREF_PLAN_THINK_ON_STRING,   "On  (empty=auto):", thinkPlanString.getGroup()));
        addField(new StringFieldEditor(PeonConstants.PREF_PLAN_THINK_OFF_STRING,  "Off (empty=nothing):", thinkPlanString.getGroup()));

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

    @Override
    public void init(IWorkbench workbench) {}
}
