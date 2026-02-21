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
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.parts.PeonConstants;

public class AiConfigPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public AiConfigPreferenceView() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PeonConstants.PLUGIN_ID));
        setDescription("Configure the AI/LLM provider settings.");
    }

    @Override
    public void createFieldEditors() {
        addField(new ComboFieldEditor(PeonConstants.PREF_PROVIDER_TYPE, "Provider Type:",
                new String[][] {
                    { "Ollama", AiProvider.OLLAMA.name() },
                    { "OpenAI-compatible (Perplexity, OpenAI, ...)", AiProvider.OPEN_AI.name() },
                    { "Google Gemini", AiProvider.GOOGLE_GEMINI.name() }
                },
                getFieldEditorParent()));
        addField(new StringFieldEditor(PeonConstants.PREF_MODEL, "Model:", getFieldEditorParent()));
        addField(new StringFieldEditor(PeonConstants.PREF_URL, "URL (incl. port):", getFieldEditorParent()));
        addField(new IntegerFieldEditor(PeonConstants.PREF_TOKEN_WINDOW, "Token Window:", getFieldEditorParent()));
        addField(new BooleanFieldEditor(PeonConstants.PREF_THINKING_ENABLED, "Supports Thinking", getFieldEditorParent()));
        addField(new StringFieldEditor(PeonConstants.PREF_API_KEY, "API Key:", getFieldEditorParent()));
        addField(new StringFieldEditor(PeonConstants.PREF_SKILL_DIRECTORY, "Skills directory:", getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {}
}
