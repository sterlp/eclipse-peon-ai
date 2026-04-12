package org.sterl.llmpeon.parts.config;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.parts.PeonConstants;

public class VoicePreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private BooleanFieldEditor enabledEditor;
    private StringFieldEditor modelEditor;
    private StringFieldEditor endpointEditor;
    private StringFieldEditor baseUrlEditor;
    private StringFieldEditor apiKeyEditor;
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
        addField(new ComboFieldEditor(PeonConstants.PREF_VOICE_MIXER,
                "Microphone:", buildMixerEntries(), getFieldEditorParent()));

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

        apiKeyEditor = new StringFieldEditor(PeonConstants.PREF_VOICE_API_KEY,
                "API Key:", getFieldEditorParent());
        apiKeyEditor.getLabelControl(getFieldEditorParent())
                .setToolTipText("Leave empty to use the main provider API key configured in AI Peon Configuration");
        addField(apiKeyEditor);

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

        Link link = new Link(getFieldEditorParent(), SWT.NONE);
        link.setText("Mac users: see <a href=\"https://peon-ai-4e.sterl.org/setup/voice-config#macos-microphone-permissions\">macOS microphone permission settings</a>.");
        GridData linkGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        linkGd.horizontalSpan = 2;
        link.setLayoutData(linkGd);
        link.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> Program.launch(e.text)));

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
        apiKeyEditor.setEnabled(enabled, getFieldEditorParent());
        languageEditor.setEnabled(enabled, getFieldEditorParent());
    }

    @Override
    public void init(IWorkbench workbench) {}

    /** Builds combo entries for all input mixers available on this system. */
    private static String[][] buildMixerEntries() {
        List<String[]> entries = new ArrayList<>();
        entries.add(new String[]{"System Default", ""});
        for (javax.sound.sampled.Mixer.Info info : javax.sound.sampled.AudioSystem.getMixerInfo()) {
            javax.sound.sampled.Mixer mixer = javax.sound.sampled.AudioSystem.getMixer(info);
            javax.sound.sampled.DataLine.Info targetInfo = new javax.sound.sampled.DataLine.Info(
                javax.sound.sampled.TargetDataLine.class, null);
            if (mixer.isLineSupported(targetInfo)) {
                entries.add(new String[]{info.getName(), info.getName()});
            }
        }
        return entries.toArray(new String[0][]);
    }
}
