package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.MessageDialog;
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
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.PeonConstants;

public class AiConfigPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private ComboFieldEditor providerEditor;
    private StringFieldEditor urlEditor;
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
                    { "LM Studio (OpenAI-compatible, HTTP/1.1)", AiProvider.LM_STUDIO.name() },
                    { "OpenAI-compatible (Perplexity, OpenAI, ...)", AiProvider.OPEN_AI.name() },
                    { "Google Gemini", AiProvider.GOOGLE_GEMINI.name() },
                    { "Mistral", AiProvider.MISTRAL.name() },
                    { "Anthropic Claude", AiProvider.ANTHROPIC.name() },
                    { "GitHub Models (PAT)", AiProvider.GITHUB_MODELS.name() },
                    { "GitHub Copilot (subscription)", AiProvider.GITHUB_COPILOT.name() }
                },
                getFieldEditorParent());
        addField(providerEditor);
        addField(new StringFieldEditor(PeonConstants.PREF_MODEL, "Model:", getFieldEditorParent()));
        urlEditor = new StringFieldEditor(PeonConstants.PREF_URL, "URL (incl. port):", getFieldEditorParent());
        addField(urlEditor);

        buildCheckUrl();

        addField(new IntegerFieldEditor(PeonConstants.PREF_TOKEN_WINDOW, "Token Window:", getFieldEditorParent()));
        addField(new BooleanFieldEditor(PeonConstants.PREF_THINKING_ENABLED, "Supports Thinking", getFieldEditorParent()));
        apiKeyEditor = new StringFieldEditor(PeonConstants.PREF_API_KEY, "API Key:", getFieldEditorParent());
        addField(apiKeyEditor);
        addField(new StringFieldEditor(PeonConstants.PREF_SKILL_DIRECTORY, "Skills directory:", getFieldEditorParent()));

        buildGithubLogin();

        Link link = new Link(getFieldEditorParent(), SWT.NONE);
        link.setText("See <a href=\"https://peon-ai-4e.sterl.org/setup/configuration\">online configuration guide</a> for help.");
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.horizontalSpan = 2;
        link.setLayoutData(gd);
        link.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> Program.launch(e.text)));
    }

    private void buildCheckUrl() {
        Button btnCheckUrl = new Button(getFieldEditorParent(), SWT.PUSH);
        btnCheckUrl.setText("Check Host and Port...");
        btnCheckUrl.setToolTipText("Tests TCP connectivity to the configured URL (3s timeout)");
        GridData checkGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        checkGd.horizontalSpan = 2;
        btnCheckUrl.setLayoutData(checkGd);
        btnCheckUrl.addListener(SWT.Selection, e -> {
            String urlValue = urlEditor.getStringValue();
            boolean ok = LlmConfig.newConfig("", urlValue).isReachable(3000);
            if (ok) {
                MessageDialog.openInformation(getShell(), "Host Check", "Successfully connected to:\n" + urlValue);
            } else {
                MessageDialog.openError(getShell(), "Host Check", "Cannot reach:\n" + urlValue);
            }
        });
    }

    private void buildGithubLogin() {
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
    }

    @Override
    public void init(IWorkbench workbench) {}
}
