package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.parts.PeonConstants;

public class VoicePreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private BooleanFieldEditor enabledEditor;
    private StringFieldEditor modelEditor;
    private StringFieldEditor endpointEditor;
    private StringFieldEditor baseUrlEditor;
    private StringFieldEditor languageEditor;

    public VoicePreferenceView() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PeonConstants.PLUGIN_ID));
        setDescription("Configure speech-to-text (voice input) settings.\n"
            + "Requires a Whisper-compatible /v1/audio/transcriptions endpoint\n"
            + "(OpenAI, LM Studio, or Ollama with a Whisper model loaded).");
    }

    @Override
    public void createFieldEditors() {
        enabledEditor = new BooleanFieldEditor(PeonConstants.PREF_VOICE_ENABLED,
                "Enable Voice Input", getFieldEditorParent());
        addField(enabledEditor);

        modelEditor = new StringFieldEditor(PeonConstants.PREF_VOICE_MODEL,
                "STT Model:", getFieldEditorParent());
        modelEditor.getLabelControl(getFieldEditorParent())
                .setToolTipText("Model name sent to the transcription endpoint (e.g. whisper-1, whisper)");
        addField(modelEditor);

        endpointEditor = new StringFieldEditor(PeonConstants.PREF_VOICE_ENDPOINT,
                "Transcription Endpoint:", getFieldEditorParent());
        endpointEditor.getLabelControl(getFieldEditorParent())
                .setToolTipText("Path appended to the base URL (default: /v1/audio/transcriptions)");
        addField(endpointEditor);

        baseUrlEditor = new StringFieldEditor(PeonConstants.PREF_VOICE_BASE_URL,
                "Base URL:", getFieldEditorParent());
        baseUrlEditor.getLabelControl(getFieldEditorParent())
                .setToolTipText("Leave empty to use the main provider URL configured in AI Peon Configuration");
        addField(baseUrlEditor);

        languageEditor = new StringFieldEditor(PeonConstants.PREF_VOICE_LANGUAGE,
                "Language:", getFieldEditorParent());
        languageEditor.getLabelControl(getFieldEditorParent())
                .setToolTipText("BCP-47 language code for better accuracy (e.g. en, de) — leave empty for auto-detect");
        addField(languageEditor);

        Label hint = new Label(getFieldEditorParent(), SWT.NONE);
        hint.setText("Language examples: en, de, fr, es, ja — empty = auto-detect");
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.horizontalSpan = 2;
        hint.setLayoutData(gd);

        // Defer until after initialize() loads saved values from the preference store
        hint.getDisplay().asyncExec(() -> updateFieldStates(
            InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID)
                .getBoolean(PeonConstants.PREF_VOICE_ENABLED, false)));
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        super.propertyChange(event);
        if (event.getSource() == enabledEditor) {
            updateFieldStates(Boolean.TRUE.equals(event.getNewValue()));
        }
    }

    private void updateFieldStates(boolean enabled) {
        modelEditor.setEnabled(enabled, getFieldEditorParent());
        endpointEditor.setEnabled(enabled, getFieldEditorParent());
        baseUrlEditor.setEnabled(enabled, getFieldEditorParent());
        languageEditor.setEnabled(enabled, getFieldEditorParent());
    }

    @Override
    public void init(IWorkbench workbench) {}
}
