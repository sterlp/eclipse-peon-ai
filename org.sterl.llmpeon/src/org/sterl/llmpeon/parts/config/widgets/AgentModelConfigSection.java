package org.sterl.llmpeon.parts.config.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConnectionIdentity;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.provider.ThinkSupport;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * One per-agent model-config section (url / api-key / model / think / extra-body JSON) on the
 * advanced config page. The think widget form and the extra-body visibility are driven by the
 * <b>base</b> provider's {@link org.sterl.llmpeon.provider.LlmProvider} (provider.md R5/R3) — the
 * provider itself stays base-level.
 *
 * <p>The model combo is filled from the provider's model list, cached per
 * {@link ConnectionIdentity} in {@link ModelListCache}: fetched once when the page opens (or on
 * the refresh button), never while typing. A failed or empty list falls back to the configured
 * model only — no auto-switch. SWT is encapsulated here; the value mappings live in the SWT-free
 * {@link ThinkValueSupport} (unit-testable without a Display).</p>
 */
public class AgentModelConfigSection extends Composite {

    private final String agentId;
    private final LlmConfig base;
    private final ThinkSupport thinkForm;
    private final Text urlText;
    private final Text keyText;
    private CCombo modelCombo;
    private Text jsonText;

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

    // --- model list (per ConnectionIdentity, cached on success only) ---

    /** Fetches the model list for the current widget values (page open). Cached per identity. */
    public void fetchModels() {
        var snapshot = prepareFetch(); // UI thread: capture widget state before the background Job
        Job.create("Loading models (" + agentId + ")", monitor -> {
            var list = ModelListCache.instance().getOrFetch(snapshot.identity(), fetchList(snapshot));
            applyModelList(list, snapshot.identity());
            return Status.OK_STATUS;
        }).schedule();
    }

    /** Manual refresh: always refetches; a failed fetch keeps the previous list. */
    private void refreshModels() {
        var snapshot = prepareFetch(); // UI thread: capture widget state before the background Job
        Job.create("Refreshing models (" + agentId + ")", monitor -> {
            var cache = ModelListCache.instance();
            var list = cache.refresh(snapshot.identity(), fetchList(snapshot));
            applyModelList(list != null ? list : cache.cached(snapshot.identity()), snapshot.identity());
            return Status.OK_STATUS;
        }).schedule();
    }

    private ConnectionIdentity currentIdentity() {
        return base.effectiveConnectionFor(getRecord()).identity();
    }

    /**
     * UI-thread snapshot of the effective connection for the current widget values, captured
     * before the background fetch Job starts. The Job body reads only this snapshot — never the
     * widgets (SWT widgets are not thread-safe; reading them off the UI thread throws
     * {@code SWTException: Invalid thread access}).
     */
    public record FetchSnapshot(ConnectionIdentity identity, LlmConfig buildConfig) {}

    /** Captures the fetch snapshot on the UI thread (the only place the widgets are read for a fetch). */
    private FetchSnapshot prepareFetch() {
        var effective = base.effectiveConnectionFor(getRecord());
        return new FetchSnapshot(effective.identity(), effective.buildConfig());
    }

    /** SWT-free: the fetcher for a captured snapshot — safe to run in a background Job. */
    public static Supplier<List<AiModel>> fetchList(FetchSnapshot snapshot) {
        return () -> LlmProviders.of(snapshot.identity().provider()).listAiModels(snapshot.buildConfig());
    }

    private void applyModelList(List<AiModel> fetched, ConnectionIdentity identity) {
        EclipseUtil.runInUiThread(this, () -> {
            if (!identity.equals(currentIdentity())) return; // config changed while fetching — stale
            var items = new ArrayList<String>();
            if (fetched != null) items.addAll(fetched.stream().map(AiModel::getId).toList());
            var configured = StringUtil.stripToNull(modelCombo.getText());
            if (configured != null && !items.contains(configured)) items.add(configured);
            modelCombo.setItems(items.toArray(String[]::new));
            if (configured != null) {
                var idx = modelCombo.indexOf(configured);
                if (idx >= 0) modelCombo.select(idx);
                else modelCombo.setText(configured);
            }
        });
    }

    // --- widget construction ---

    private void buildModel() {
        addLabel("Model:");
        var row = new Composite(this, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setLayout(new GridLayout(2, false));
        modelCombo = new CCombo(row, SWT.BORDER);
        modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        var refresh = new Button(row, SWT.PUSH);
        refresh.setText("Refresh");
        refresh.setToolTipText("Reload the model list for this agent's connection");
        refresh.addListener(SWT.Selection, e -> refreshModels());
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
