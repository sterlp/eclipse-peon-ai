package org.sterl.llmpeon.parts.config.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.provider.ExtraBodyExamples;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.provider.ThinkSupport;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * One per-agent model-config section (url / api-key / model / think / temperature / extra-body JSON) on the
 * advanced config page. The think widget form and the extra-body visibility are driven by the
 * <b>base</b> provider's {@link org.sterl.llmpeon.provider.LlmProvider} (provider.md R5/R3) — the
 * provider itself stays base-level.
 *
 * <p>The model dropdown + refresh lives in the shared {@link ModelComboWidget}: fetched once
 * when the page opens (or on the refresh button), never while typing; a failed or empty list
 * falls back to the configured model only. SWT is encapsulated here; the value mappings live in
 * the SWT-free {@link ThinkValueSupport} (unit-testable without a Display).</p>
 */
public class AgentModelConfigSection extends Composite {

    private final String agentId;
    private final LlmConfig base;
    private final ThinkSupport thinkForm;
    private final Text urlText;
    private final Text keyText;
    private final ModelComboWidget modelWidget;
    private final Text temperatureText;
    private Text jsonText;
    private Label examplesLabel;

    // exactly one of these is non-null, per thinkForm
    private Button thinkCheck;
    private CCombo thinkCombo;
    private Text thinkText;

    public AgentModelConfigSection(Composite parent, String agentId, LlmConfig base) {
        super(parent, SWT.NONE);
        this.agentId = agentId;
        this.base = base;
        var provider = LlmProviders.of(base.getProviderType());
        this.thinkForm = provider.thinkSupport();
        setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
        setLayout(new GridLayout(2, false));
        this.urlText = addLabeledText("URL (empty = inherit base):");
        this.keyText = addLabeledText("API Key (empty = inherit base):");
        this.modelWidget = new ModelComboWidget(this, agentId, this::prepareFetch);
        buildThink();
        this.temperatureText = addLabeledText("Temperature (empty = unset):");
        buildJson(provider.supportsExtraBody());
    }

    public String getAgentId() {
        return agentId;
    }

    /** Populates the widgets from the given record (null fields show as empty). */
    public void load(AgentModelConfig record) {
        urlText.setText(StringUtil.stripToEmpty(record.url()));
        keyText.setText(StringUtil.stripToEmpty(record.apiKey()));
        modelWidget.setModel(record.model());
        loadThink(record.think());
        temperatureText.setText(StringUtil.stripToEmpty(record.temperature()));
        if (jsonText != null) jsonText.setText(StringUtil.stripToEmpty(record.extraBody()));
    }

    /** Reads the widgets back into a record (empty fields become null). */
    public AgentModelConfig getRecord() {
        return new AgentModelConfig(
                StringUtil.stripToNull(urlText.getText()),
                StringUtil.stripToNull(keyText.getText()),
                StringUtil.stripToNull(modelWidget.getModel()),
                readThink(),
                jsonText != null ? StringUtil.stripToNull(jsonText.getText()) : null,
                StringUtil.stripToNull(temperatureText.getText()));
    }

    /** Fetches the model list for the current widget values (page open). Cached per identity. */
    public void fetchModels() {
        modelWidget.fetchModels();
    }

    /**
     * UI-thread snapshot of the effective connection for the current widget values, captured
     * before the background fetch Job starts. The Job body reads only this snapshot — never the
     * widgets (SWT widgets are not thread-safe; reading them off the UI thread throws
     * {@code SWTException: Invalid thread access}).
     */
    private ModelComboWidget.FetchSnapshot prepareFetch() {
        var effective = base.effectiveConnectionFor(getRecord());
        return new ModelComboWidget.FetchSnapshot(effective.identity(), effective.buildConfig());
    }

    // --- widget construction ---

    private void buildThink() {
        if (thinkForm instanceof ThinkSupport.Boolean) {
            addLabel("Think:");
            thinkCheck = new Button(this, SWT.CHECK);
            thinkCheck.setText("Enabled");
            thinkCheck.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        } else if (thinkForm instanceof ThinkSupport.Values v) {
            addLabel("Think:");
            thinkCombo = new CCombo(this, SWT.BORDER);
            thinkCombo.setItems(ThinkValueSupport.valuesItems(v).toArray(String[]::new));
            thinkCombo.select(0);
            thinkCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        } else if (thinkForm instanceof ThinkSupport.FreeString || thinkForm instanceof ThinkSupport.Unknown) {
            addLabel("Think (empty = off):");
            thinkText = new Text(this, SWT.BORDER);
            thinkText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        }
        // ThinkSupport.None → no per-request think input, widget hidden
    }

    private void buildJson(boolean visible) {
        if (!visible) return;
        addLabel("Extra body (JSON):");
        jsonText = new Text(this, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        gd.horizontalSpan = 2;
        gd.heightHint = 80;
        jsonText.setLayoutData(gd);
        buildJsonExamples();
    }

    /**
     * Paste-ready extra-body examples under the JSON input (caching.md R3): a single compact row
     * (label + one button per example, tooltip = description) and a status label. Built only when
     * {@code supportsExtraBody()} — the provider gate. A paste simply replaces the field content
     * (2c D2, no dialog).
     */
    private void buildJsonExamples() {
        var examples = ExtraBodyExamples.all();
        var row = new Composite(this, SWT.NONE);
        var rowGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        rowGd.horizontalSpan = 2;
        row.setLayoutData(rowGd);
        row.setLayout(new GridLayout(examples.size() + 1, false));
        var label = new Label(row, SWT.NONE);
        label.setText("Examples:");
        for (var example : examples) {
            var button = new Button(row, SWT.PUSH);
            button.setText(example.name());
            button.setToolTipText(example.description());
            button.addListener(SWT.Selection, e -> pasteExample(example));
        }
        examplesLabel = new Label(this, SWT.NONE);
        var labelGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        labelGd.horizontalSpan = 2;
        examplesLabel.setLayoutData(labelGd);
    }

    private void pasteExample(ExtraBodyExamples.Example example) {
        jsonText.setText(example.json());
        examplesLabel.setText(example.name() + " example inserted.");
    }

    private Text addLabeledText(String label) {
        addLabel(label);
        var text = new Text(this, SWT.BORDER);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return text;
    }

    private void addLabel(String text) {
        var label = new Label(this, SWT.NONE);
        label.setText(text);
        label.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
    }

    // --- think value mapping (delegates to the SWT-free helper) ---

    private void loadThink(String stored) {
        if (thinkForm instanceof ThinkSupport.Boolean) {
            thinkCheck.setSelection(ThinkValueSupport.booleanOn(stored));
        } else if (thinkForm instanceof ThinkSupport.Values) {
            var display = ThinkValueSupport.valuesDisplay(stored);
            int idx = thinkCombo.indexOf(display);
            if (idx >= 0) thinkCombo.select(idx);
            else thinkCombo.setText(display); // unknown value → shown verbatim
        } else if (thinkForm instanceof ThinkSupport.FreeString || thinkForm instanceof ThinkSupport.Unknown) {
            thinkText.setText(StringUtil.stripToEmpty(stored));
        }
        // ThinkSupport.None → hidden
    }

    private String readThink() {
        if (thinkForm instanceof ThinkSupport.Boolean) {
            return ThinkValueSupport.booleanValue(thinkCheck.getSelection());
        } else if (thinkForm instanceof ThinkSupport.Values) {
            return ThinkValueSupport.valuesStored(thinkCombo.getText());
        } else if (thinkForm instanceof ThinkSupport.FreeString || thinkForm instanceof ThinkSupport.Unknown) {
            return StringUtil.stripToEmpty(thinkText.getText());
        }
        return ""; // ThinkSupport.None
    }
}
