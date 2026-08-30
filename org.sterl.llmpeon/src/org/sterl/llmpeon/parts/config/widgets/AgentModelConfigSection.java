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
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.provider.ThinkSupport;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * One per-agent model-config section (url / api-key / model / think / extra-body JSON) on the
 * advanced config page. The think widget form and the extra-body visibility are driven by the
 * <b>base</b> provider's {@link org.sterl.llmpeon.provider.LlmProvider} (provider.md R5/R3) — the
 * provider itself stays base-level.
 *
 * <p>SWT is encapsulated here; the value mappings live in the SWT-free {@link ThinkValueSupport}
 * (unit-testable without a Display). The model list is an editable combo — populating it from the
 * provider happens in the model-list increment (this increment only provides the control).</p>
 */
public class AgentModelConfigSection extends Composite {

    private final String agentId;
    private final ThinkSupport thinkForm;
    private final Text urlText;
    private final Text keyText;
    private CCombo modelCombo;
    private Text jsonText;

    // exactly one of these is non-null, per thinkForm
    private Button thinkCheck;
    private CCombo thinkCombo;
    private Text thinkText;

    public AgentModelConfigSection(Composite parent, String agentId, AiProvider baseProvider) {
        super(parent, SWT.NONE);
        this.agentId = agentId;
        var provider = LlmProviders.of(baseProvider);
        this.thinkForm = provider.thinkSupport();
        setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
        setLayout(new GridLayout(2, false));
        this.urlText = addLabeledText("URL (empty = inherit base):");
        this.keyText = addLabeledText("API Key (empty = inherit base):");
        buildModel();
        buildThink();
        buildJson(provider.supportsExtraBody());
    }

    public String getAgentId() {
        return agentId;
    }

    /** Populates the widgets from the given record (null fields show as empty). */
    public void load(AgentModelConfig record) {
        urlText.setText(StringUtil.stripToEmpty(record.url()));
        keyText.setText(StringUtil.stripToEmpty(record.apiKey()));
        modelCombo.setText(StringUtil.stripToEmpty(record.model()));
        loadThink(record.think());
        if (jsonText != null) jsonText.setText(StringUtil.stripToEmpty(record.extraBody()));
    }

    /** Reads the widgets back into a record (empty fields become null). */
    public AgentModelConfig getRecord() {
        return new AgentModelConfig(
                StringUtil.stripToNull(urlText.getText()),
                StringUtil.stripToNull(keyText.getText()),
                StringUtil.stripToNull(modelCombo.getText()),
                readThink(),
                jsonText != null ? StringUtil.stripToNull(jsonText.getText()) : null);
    }

    // --- widget construction ---

    private void buildModel() {
        addLabel("Model:");
        modelCombo = new CCombo(this, SWT.BORDER);
        modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

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
