package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.parts.PeonConstants;

public class AiConfigPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private ComboFieldEditor providerEditor;
    private StringFieldEditor apiKeyEditor;

    public AiConfigPreferenceView() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PeonConstants.PLUGIN_ID));
        setDescription("Configure the AI/LLM provider settings.");
    }

    @Override
    public void createFieldEditors() {
        providerEditor = new ComboFieldEditor(PeonConstants.PREF_PROVIDER_TYPE, "Provider Type:",
                new String[][] {
                    { "Ollama", AiProvider.OLLAMA.name() },
                    { "OpenAI-compatible (Perplexity, OpenAI, ...)", AiProvider.OPEN_AI.name() },
                    { "Google Gemini", AiProvider.GOOGLE_GEMINI.name() },
                    { "Mistral", AiProvider.MISTRAL.name() },
                    { "GitHub Copilot", AiProvider.GITHUB_COPILOT.name() }
                },
                getFieldEditorParent());
        addField(providerEditor);
        addField(new StringFieldEditor(PeonConstants.PREF_MODEL, "Model:", getFieldEditorParent()));
        addField(new StringFieldEditor(PeonConstants.PREF_URL, "URL (incl. port):", getFieldEditorParent()));
        addField(new IntegerFieldEditor(PeonConstants.PREF_TOKEN_WINDOW, "Token Window:", getFieldEditorParent()));
        addField(new BooleanFieldEditor(PeonConstants.PREF_THINKING_ENABLED, "Supports Thinking", getFieldEditorParent()));
        apiKeyEditor = new StringFieldEditor(PeonConstants.PREF_API_KEY, "API Key:", getFieldEditorParent());
        addField(apiKeyEditor);
        addField(new StringFieldEditor(PeonConstants.PREF_SKILL_DIRECTORY, "Skills directory:", getFieldEditorParent()));

        // GitHub Copilot login button (spans both grid columns like the help link below)
        Button btnLogin = new Button(getFieldEditorParent(), SWT.PUSH);
        btnLogin.setText("Login with GitHub Copilot...");
        btnLogin.setToolTipText("Opens the GitHub Device Flow to obtain an OAuth token for Copilot");
        GridData btnGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        btnGd.horizontalSpan = 2;
        btnLogin.setLayoutData(btnGd);
        btnLogin.addListener(SWT.Selection, e -> {
            new CopilotDeviceFlowDialog(getShell()).open();
            // Reload field editors so the page shows the saved token + provider,
            // preventing stale values from overwriting on OK/Apply.
            providerEditor.load();
            apiKeyEditor.load();
        });

        Link link = new Link(getFieldEditorParent(), SWT.NONE);
        link.setText("See <a href=\"https://peon-ai-4e.sterl.org/setup/configuration\">online configuration guide</a> for help.");
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.horizontalSpan = 2;
        link.setLayoutData(gd);
        link.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> Program.launch(e.text)));
    }

    @Override
    public void init(IWorkbench workbench) {}
}
