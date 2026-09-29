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
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfigSaver;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.widgets.ModelConfigWidget;

public class AiConfigPreferenceView extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private ModelConfigWidget modelConfigWidget;

    public AiConfigPreferenceView() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PeonConstants.PLUGIN_ID));
        setDescription("Configure the AI/LLM provider settings.");
    }

    @Override
    public void createFieldEditors() {
        // The connection fields (provider · URL · API key · model · think · temperature) plus Ping
        // live in the ModelConfigWidget: Ping and Reload read the widget's live values, only
        // OK/Apply persists.
        modelConfigWidget = new ModelConfigWidget(getFieldEditorParent(), "base",
                LlmPreferenceInitializer::buildWithDefaults);
        modelConfigWidget.load(storeValues());
        modelConfigWidget.fetchModels();

        addField(new IntegerFieldEditor(PeonConstants.PREF_TOKEN_WINDOW, "Auto compact after:", getFieldEditorParent()));

        addField(new BooleanFieldEditor(PeonConstants.PREF_SEND_THINKING_ENABLED,
                "Resend model thinking - needed by most LLMs like Qwen 3.x, Mistral, DeepSeek", getFieldEditorParent()));

        buildGithubLogin();

        addField(new BooleanFieldEditor(PeonConstants.PREF_DISK_TOOLS_ENABLED,
                "Enable Disk File Tools (outside Eclipse workspace)", getFieldEditorParent()));

        addField(new ComboFieldEditor(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "Shell Command Confirmation:",
                new String[][] { 
                    { "Not Required", "false" }, 
                    { "Always Required", "always" },
                    { "Except in Peon-PO / autonomous mode", "not-autonomous" } 
                },
                getFieldEditorParent()));

        addField(new StringFieldEditor(PeonConstants.PREF_CONFIG_DIRECTORY, "Config directory:", 
                getFieldEditorParent()));

        Link link = new Link(getFieldEditorParent(), SWT.NONE);
        link.setText(
                "See <a href=\"https://peon-ai-4e.sterl.org/setup/configuration\">online configuration guide</a> for help.");
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.horizontalSpan = 2;
        link.setLayoutData(gd);
        link.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> Program.launch(e.text)));
    }

    /**
     * The widget's values from the store — single load point, also used to reload the widget
     * after the GitHub login flow (the flow writes provider + key directly to the store).
     */
    private ModelConfigWidget.ConnectionValues storeValues() {
        var store = getPreferenceStore();
        var devRecord = LlmPreferenceInitializer.buildWithDefaults().modelConfigFor(AgentModelConfig.DEV);
        return new ModelConfigWidget.ConnectionValues(providerOrNull(store.getString(PeonConstants.PREF_PROVIDER_TYPE)),
                store.getString(PeonConstants.PREF_URL), store.getString(PeonConstants.PREF_API_KEY), devRecord.think(),
                store.getString(PeonConstants.PREF_MODEL), devRecord.temperature(), devRecord.extraBody());
    }

    private static AiProvider providerOrNull(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return AiProvider.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null; // unknown stored value → the widget falls back to the first entry
        }
    }

    @Override
    public boolean performOk() {
        if (!super.performOk()) {
            return false;
        }
        var values = modelConfigWidget.getValues();
        var store = new EclipseLlmConfigStore(InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID));
        // Dev is the default slot (R-DEF-4): the widget writes the base keys — an empty url/key
        // removes the key (empty = unset, never a null put: JFace's setValue(name, null) NPEs and
        // leaves a partial save).
        store.put(PeonConstants.PREF_PROVIDER_TYPE, values.provider().name());
        EclipseLlmConfigStore.putOrRemove(store, PeonConstants.PREF_URL, values.url());
        EclipseLlmConfigStore.putOrRemove(store, PeonConstants.PREF_API_KEY, values.apiKey());
        // The dev record is the base model: the saver writes llm.model + llm.agent.dev.think/temperature,
        // keeps the loaded extraBody, and removes the legacy llm.agent.dev.url/apiKey overrides —
        // dev is the default slot, it has no override keys (ADR-0062 clean break).
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.DEV,
                LlmPreferenceInitializer.buildWithDefaults().modelConfigFor(AgentModelConfig.DEV).withModel(values.model())
                        .withThink(values.think()).withTemperature(values.temperature()));
        return true;
    }

    private void buildGithubLogin() {
        // GitHub Copilot login button (spans both grid columns like the help link
        // below)
        Button btnLogin = new Button(getFieldEditorParent(), SWT.PUSH);
        btnLogin.setText("Login with GitHub Copilot...");
        btnLogin.setToolTipText("Opens the GitHub Device Flow to obtain an OAuth token for Copilot");
        GridData btnGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        btnGd.horizontalSpan = 2;
        btnLogin.setLayoutData(btnGd);
        btnLogin.addListener(SWT.Selection, e -> {
            new CopilotDeviceFlowDialog(getShell()).open();
            // Reload the widget so the page shows the saved token + provider,
            // preventing stale values from overwriting on OK/Apply.
            modelConfigWidget.load(storeValues());
        });
    }

    @Override
    public void init(IWorkbench workbench) {
    }
}
