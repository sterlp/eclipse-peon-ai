package org.sterl.llmpeon.parts.config.widgets;

import java.util.function.Supplier;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.provider.ThinkSupport;
import org.sterl.llmpeon.provider.ThinkValueSupport;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * The basic config page's connection field group (provider · URL · API key · model · think) plus
 * the ping button — a plain controller (no SWT parent of its own, like {@link ModelComboWidget}):
 * it creates the fields directly in the given 2-column parent grid so they sit in the same field
 * column as the page's other fields.
 *
 * <p><b>Live read (ADR-0060):</b> {@link #snapshot()} and {@link #computePing()} read the
 * currently typed widget values, not the preference store — reload and ping act on what the user
 * sees, without Apply. The widget persists nothing; the page routes the store (ADR-0005). The
 * {@code base} supplier supplies the store state for the non-identity transport parameters
 * (timeouts, header params) at snapshot time; provider/url/key are overridden with the live
 * widget values.</p>
 *
 * <p><b>Provider-dependent think field (R-MCW-6):</b> the think field's form follows the
 * selected provider's {@link ThinkSupport} and is rebuilt live on a provider-combo selection —
 * unlike the advanced page, which freezes the form at construction. Carry-over on a provider
 * change: verbatim where the new form allows free input (toggle combo / free text), cleared for a
 * fixed list without a match (never a silent replacement), dropped for {@link ThinkSupport.None}.
 * Think is not part of the {@link org.sterl.llmpeon.ai.ConnectionIdentity} and never flows into
 * {@link #snapshot()}.</p>
 *
 * <p><b>Constructor contract:</b> the parent is the page's 2-column grid; the widget creates its
 * own labels in the JFace Field-Editor style (SWT.LEFT, no GridData).</p>
 */
public class ModelConfigWidget {

    /** Label → provider entries, 1:1 from the basic page (no user-visible change). */
    private static final String[][] PROVIDER_ENTRIES = {
            { "OpenAI (llama.cpp, unsloth, OmniRoute)", AiProvider.OPEN_AI.name() },
            { "LM Studio (OpenAI-compatible)", AiProvider.LM_STUDIO.name() },
            { "Ollama", AiProvider.OLLAMA.name() },
            { "OpenAI-Official Azure Foundry", AiProvider.OPEN_AI_OFFICIAL.name() },
            { "OpenAI-GitHub Copilot (subscription)", AiProvider.GITHUB_COPILOT.name() },
            { "Google Gemini", AiProvider.GOOGLE_GEMINI.name() },
            { "Mistral", AiProvider.MISTRAL.name() },
            { "Anthropic Claude", AiProvider.ANTHROPIC.name() },
            { "GitHub Models (PAT)", AiProvider.GITHUB_MODELS.name() } };

    private final Composite parent;
    private final Supplier<LlmConfig> base;
    private final Combo providerCombo;
    private final Text urlText;
    private final Text keyText;
    private final ModelComboWidget modelWidget;

    // exactly one of thinkCombo/thinkText is non-null, per thinkForm (None → neither)
    private ThinkSupport thinkForm;
    private Label thinkLabel;
    private Combo thinkCombo;
    private Text thinkText;

    /**
     * @param parent the 2-column grid to build into (the basic page)
     * @param jobName used in the background Job names (e.g. "base")
     * @param base UI-thread supplier of the store state (transport parameters); provider/url/key
     *             are overridden with the live widget values in {@link #snapshot()}
     */
    public ModelConfigWidget(Composite parent, String jobName, Supplier<LlmConfig> base) {
        this.parent = parent;
        this.base = base;

        addLabel("Provider Type:");
        providerCombo = new Combo(parent, SWT.READ_ONLY);
        providerCombo.setItems(labels());
        providerCombo.select(0);
        providerCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        providerCombo.addListener(SWT.Selection, e -> rebuildThinkOnProviderChange());

        addLabel("URL (incl. port):");
        urlText = new Text(parent, SWT.BORDER);
        urlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        var ping = new Button(parent, SWT.PUSH);
        ping.setText("Ping");
        ping.setToolTipText("Tests TCP connectivity (host and port) to the URL above (3s timeout)");
        var pingGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        pingGd.horizontalSpan = 2;
        ping.setLayoutData(pingGd);
        ping.addListener(SWT.Selection, e -> showPingResult());

        addLabel("API Key:");
        keyText = new Text(parent, SWT.BORDER);
        keyText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        addLabel("Model:"); // ModelComboWidget contract: label before the widget
        modelWidget = new ModelComboWidget(parent, jobName, this::snapshot);

        rebuildThink(LlmProviders.of(provider()).thinkSupport(), null, false);
    }

    /** The connection values as currently shown (null fields = empty, think "" = unset). */
    public record ConnectionValues(AiProvider provider, String url, String apiKey, String think, String model) {
        @Override
        public String toString() {
            return "ConnectionValues[provider=%s, url=%s, apiKey=***, think=%s, model=%s]"
                    .formatted(provider, url, think, model);
        }
    }

    /** The ping outcome for the widget's current URL (no credential — the key never leaves this type). */
    public record PingResult(String url, boolean reachable) {
    }

    /** Populates the widgets from the given values (null-safe; builds the think field per provider form). */
    public void load(ConnectionValues values) {
        var v = values == null ? new ConnectionValues(null, null, null, null, null) : values;
        var idx = providerIndex(v.provider());
        providerCombo.select(idx >= 0 ? idx : 0); // unknown stored value → first entry (ComboFieldEditor parity)
        urlText.setText(StringUtil.stripToEmpty(v.url()));
        keyText.setText(StringUtil.stripToEmpty(v.apiKey()));
        modelWidget.setModel(v.model());
        rebuildThink(LlmProviders.of(provider()).thinkSupport(), v.think(), false);
    }

    /** Reads the widgets back (UI thread); empty fields become null, think "" = unset. */
    public ConnectionValues getValues() {
        return new ConnectionValues(provider(),
                StringUtil.stripToNull(urlText.getText()),
                StringUtil.stripToNull(keyText.getText()),
                readThink(),
                StringUtil.stripToNull(modelWidget.getModel()));
    }

    /**
     * UI-thread snapshot of the effective connection for the current widget values (live read,
     * ADR-0060): the store state's transport parameters with provider/url/key overridden by the
     * live widget values. Think is not part of the identity and is not included.
     */
    public ModelComboWidget.FetchSnapshot snapshot() {
        var v = getValues();
        var live = base.get().toBuilder()
                .providerType(v.provider())
                .url(v.url())
                .apiKey(v.apiKey())
                .build();
        return ModelComboWidget.baseSnapshot(live);
    }

    /** Pure ping computation for the widget's current URL (blocking, UI thread — IST parity). */
    public PingResult computePing() {
        var url = urlText.getText();
        return new PingResult(url, LlmConfig.newConfig("", url).isReachable(3000));
    }

    /** Fetches the model list for the current widget values (page open). Cached per identity. */
    public void fetchModels() {
        modelWidget.fetchModels();
    }

    // --- widget construction ---

    private static String[] labels() {
        var labels = new String[PROVIDER_ENTRIES.length];
        for (var i = 0; i < PROVIDER_ENTRIES.length; i++) labels[i] = PROVIDER_ENTRIES[i][0];
        return labels;
    }

    private Label addLabel(String text) {
        var label = new Label(parent, SWT.LEFT); // JFace Field-Editor default: SWT.LEFT, no GridData
        label.setText(text);
        return label;
    }

    private static int providerIndex(AiProvider provider) {
        if (provider == null) return -1;
        for (var i = 0; i < PROVIDER_ENTRIES.length; i++) {
            if (PROVIDER_ENTRIES[i][1].equals(provider.name())) return i;
        }
        return -1;
    }

    private AiProvider provider() {
        var idx = providerCombo.getSelectionIndex();
        return AiProvider.valueOf(PROVIDER_ENTRIES[idx >= 0 ? idx : 0][1]);
    }

    private void showPingResult() {
        var result = computePing();
        if (result.reachable()) {
            MessageDialog.openInformation(parent.getShell(), "Host Check", "Successfully connected to:\n" + result.url());
        } else {
            MessageDialog.openError(parent.getShell(), "Host Check", "Cannot reach:\n" + result.url());
        }
    }

    // --- think field (provider-dependent, live rebuild — R-MCW-6) ---

    /** Provider-combo selection: carry the current value over and rebuild for the new form. */
    private void rebuildThinkOnProviderChange() {
        var carried = readThink(); // read before disposing the old control
        rebuildThink(LlmProviders.of(provider()).thinkSupport(), carried, true);
    }

    /**
     * Rebuilds the think field for the given form — atomically (dispose + build + layout in one
     * UI-thread run, no flicker window) — and applies the value. With {@code carried=true}
     * (provider change): verbatim where the form allows free input, cleared for a fixed list
     * without a match (never a silent replacement), dropped for {@link ThinkSupport.None}. With
     * {@code carried=false} (explicit load): an unknown values-list entry is shown verbatim
     * (advanced-page parity).
     */
    private void rebuildThink(ThinkSupport form, String value, boolean carried) {
        disposeThinkField();
        thinkForm = form;
        if (form instanceof ThinkSupport.Toggle) {
            thinkLabel = addLabel("Think (Default):");
            thinkCombo = newCombo();
            thinkCombo.setItems(ThinkValueSupport.toggleItems().toArray(String[]::new));
        } else if (form instanceof ThinkSupport.Values v) {
            thinkLabel = addLabel("Think (Default):");
            thinkCombo = newCombo();
            thinkCombo.setItems(ThinkValueSupport.valuesItems(v).toArray(String[]::new));
        } else if (form instanceof ThinkSupport.FreeString || form instanceof ThinkSupport.Unknown) {
            thinkLabel = addLabel("Think (Default, empty = off):");
            thinkText = new Text(parent, SWT.BORDER);
            thinkText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        }
        // ThinkSupport.None → no field (value dropped)
        applyThinkValue(value, carried);
        parent.layout();
    }

    private void disposeThinkField() {
        if (thinkLabel != null && !thinkLabel.isDisposed()) thinkLabel.dispose();
        if (thinkCombo != null && !thinkCombo.isDisposed()) thinkCombo.dispose();
        if (thinkText != null && !thinkText.isDisposed()) thinkText.dispose();
        thinkLabel = null;
        thinkCombo = null;
        thinkText = null;
    }

    private Combo newCombo() {
        var combo = new Combo(parent, SWT.BORDER);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return combo;
    }

    private void applyThinkValue(String value, boolean carried) {
        if (thinkCombo != null) {
            if (thinkForm instanceof ThinkSupport.Values) {
                var display = ThinkValueSupport.valuesDisplay(value);
                var idx = thinkCombo.indexOf(display);
                if (idx >= 0) thinkCombo.select(idx);
                else if (carried) thinkCombo.setText(""); // fixed list, no match → unset (never a silent replacement)
                else thinkCombo.setText(display); // explicit load: unknown value shown verbatim
            } else { // Toggle: editable combo, stored value = displayed value
                thinkCombo.setText(StringUtil.stripToEmpty(value));
            }
        } else if (thinkText != null) { // FreeString/Unknown: verbatim
            thinkText.setText(StringUtil.stripToEmpty(value));
        }
        // None → no field
    }

    private String readThink() {
        if (thinkCombo != null && !thinkCombo.isDisposed()) {
            if (thinkForm instanceof ThinkSupport.Values) return ThinkValueSupport.valuesStored(thinkCombo.getText());
            return StringUtil.stripToEmpty(thinkCombo.getText()); // Toggle
        }
        if (thinkText != null && !thinkText.isDisposed()) return StringUtil.stripToEmpty(thinkText.getText());
        return ""; // None
    }
}
