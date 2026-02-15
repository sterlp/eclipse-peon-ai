package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.parts.LlmPreferenceConstants;

public class AiConfigPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public AiConfigPreferenceView() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, LlmPreferenceConstants.PLUGIN_ID));
        setDescription("Configure the AI/LLM provider settings.");
    }

    @Override
    public void createFieldEditors() {
        addField(new ComboFieldEditor(LlmPreferenceConstants.PREF_PROVIDER_TYPE, "Provider Type:",
                new String[][] { { "Ollama", "ollama" } },
                getFieldEditorParent()));
        addField(new StringFieldEditor(LlmPreferenceConstants.PREF_MODEL, "Model:", getFieldEditorParent()));
        addField(new StringFieldEditor(LlmPreferenceConstants.PREF_URL, "URL (incl. port):", getFieldEditorParent()));
        addField(new IntegerFieldEditor(LlmPreferenceConstants.PREF_TOKEN_WINDOW, "Token Window:", getFieldEditorParent()));
        addField(new BooleanFieldEditor(LlmPreferenceConstants.PREF_THINKING_ENABLED, "Supports Thinking", getFieldEditorParent()));
        addField(new StringFieldEditor(LlmPreferenceConstants.PREF_API_KEY, "API Key:", getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {}
}
