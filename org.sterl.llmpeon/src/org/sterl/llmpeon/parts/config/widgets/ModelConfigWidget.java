package org.sterl.llmpeon.parts.config.widgets;

import java.util.function.Supplier;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
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
 * The connection field group (provider · URL · API key · model · think · temperature) plus the
 * ping button, shared by the basic config page and the advanced page's dev (default) section — a
 * plain controller (no SWT parent of its own, like {@link ModelComboWidget}):
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
 * selected provider's {@link ThinkSupport} and is switched live on a provider-combo selection —
 * unlike the advanced page, which freezes the form at construction. The label/combo/text are
 * created once and toggled via {@code GridData.exclude}, so the binding field order stays
 * stable (dispose + recreate would append the new controls at the end of the parent's
 * children). Carry-over on a provider
 * change: verbatim where the new form allows free input (toggle combo / free text), cleared for a
 * fixed list without a match (never a silent replacement), dropped for {@link ThinkSupport.None}.
 * Think is not part of the {@link org.sterl.llmpeon.ai.ConnectionIdentity} and never flows into
 * {@link #snapshot()}.</p>
 *
 * <p><b>Constructor contract:</b> the parent is the page's 2-column grid; the widget creates its
 * own labels — JFace Field-Editor style ({@code SWT.LEFT}, no GridData) on the basic page,
 * right-aligned ({@code SWT.END}, GridData) on the advanced page (D4, label per caller).</p>
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
    private final int labelStyle;
    private final Supplier<LlmConfig> base;
    private final Combo providerCombo;
    private final Text urlText;
    private final Text keyText;
    private final ModelComboWidget modelWidget;

    // think field created once; the visible control follows thinkForm (None → all hidden)
    private ThinkSupport thinkForm;
    private Label thinkLabel;
    private Combo thinkCombo;
    private Text thinkText;
    private final Text temperatureText;

    /**
     * @param parent the 2-column grid to build into (the basic page)
     * @param jobName used in the background Job names (e.g. "base")
     * @param base UI-thread supplier of the store state (transport parameters); provider/url/key
     *             are overridden with the live widget values in {@link #snapshot()}
     */
    public ModelConfigWidget(Composite parent, String jobName, Supplier<LlmConfig> base) {
        this(parent, jobName, base, SWT.LEFT); // JFace Field-Editor style (basic page)
    }

    /**
     * @param parent the 2-column grid to build into
     * @param jobName used in the background Job names (e.g. "base" or "dev")
     * @param base UI-thread supplier of the store state (transport parameters); provider/url/key
     *             are overridden with the live widget values in {@link #snapshot()}
     * @param labelStyle the label alignment: {@code SWT.LEFT} (basic page, JFace Field-Editor
     *             style, no GridData) or {@code SWT.END} (advanced page, GridData)
     */
    public ModelConfigWidget(Composite parent, String jobName, Supplier<LlmConfig> base, int labelStyle) {
        this.parent = parent;
        this.labelStyle = labelStyle;
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

        // The think field (label + combo + text) is created once and switched per form via
        // GridData.exclude — dispose + recreate would append the new controls at the end of
        // the parent's children and break the binding field order once the page adds its own
        // field editors after the widget.
        thinkLabel = addLabel("Think (Default):");
        if (labelStyle != SWT.END) {
            thinkLabel.setLayoutData(new GridData()); // needs a GridData instance for the exclude toggle
        } // SWT.END labels already carry a GridData from addLabel
        thinkCombo = newCombo();
        thinkText = new Text(parent, SWT.BORDER);
        thinkText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        applyThinkForm(LlmProviders.of(provider()).thinkSupport(), null, false);

        // Temperature (R-DEF-8): request-level like think — no provider gate, always visible.
        addLabel("Temperature (empty = unset):");
        temperatureText = new Text(parent, SWT.BORDER);
        temperatureText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    /** The connection values as currently shown (null fields = empty, think "" = unset). */
    public record ConnectionValues(AiProvider provider, String url, String apiKey, String think, String model,
            String temperature) {
        @Override
        public String toString() {
            return "ConnectionValues[provider=%s, url=%s, apiKey=***, think=%s, model=%s, temperature=%s]"
                    .formatted(provider, url, think, model, temperature);
        }
    }

    /** The ping outcome for the widget's current URL (no credential — the key never leaves this type). */
    public record PingResult(String url, boolean reachable) {
    }

    /** Populates the widgets from the given values (null-safe; builds the think field per provider form). */
    public void load(ConnectionValues values) {
        var v = values == null ? new ConnectionValues(null, null, null, null, null, null) : values;
        var idx = providerIndex(v.provider());
        providerCombo.select(idx >= 0 ? idx : 0); // unknown stored value → first entry (ComboFieldEditor parity)
        urlText.setText(StringUtil.stripToEmpty(v.url()));
        keyText.setText(StringUtil.stripToEmpty(v.apiKey()));
        modelWidget.setModel(v.model());
        temperatureText.setText(StringUtil.stripToEmpty(v.temperature()));
        applyThinkForm(LlmProviders.of(provider()).thinkSupport(), v.think(), false);
    }

    /** Reads the widgets back (UI thread); empty fields become null, think "" = unset. */
    public ConnectionValues getValues() {
        return new ConnectionValues(provider(),
                StringUtil.stripToNull(urlText.getText()),
                StringUtil.stripToNull(keyText.getText()),
                readThink(),
                StringUtil.stripToNull(modelWidget.getModel()),
                StringUtil.stripToNull(temperatureText.getText()));
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
        var label = new Label(parent, labelStyle);
        label.setText(text);
        if (labelStyle == SWT.END) {
            label.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false)); // agent-section style
        }
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

    // --- think field (provider-dependent, live form switch — R-MCW-6) ---

    /** Provider-combo selection: carry the current value over and switch to the new form. */
    private void rebuildThinkOnProviderChange() {
        var carried = readThink(); // read before the form switch
        applyThinkForm(LlmProviders.of(provider()).thinkSupport(), carried, true);
    }

    /**
     * Switches the think field to the given form — atomically on the UI thread (label text,
     * combo items and the show/hide state change in one run, then one layout — no flicker
     * window) — and applies the value. With {@code carried=true} (provider change): verbatim
     * where the form allows free input, cleared for a fixed list without a match (never a
     * silent replacement), dropped for {@link ThinkSupport.None}. With {@code carried=false}
     * (explicit load): an unknown values-list entry is shown verbatim (advanced-page parity).
     */
    private void applyThinkForm(ThinkSupport form, String value, boolean carried) {
        thinkForm = form;
        boolean combo = form instanceof ThinkSupport.Toggle || form instanceof ThinkSupport.Values;
        boolean text = form instanceof ThinkSupport.FreeString || form instanceof ThinkSupport.Unknown;
        thinkLabel.setText(text ? "Think (Default, empty = off):" : "Think (Default):");
        if (form instanceof ThinkSupport.Toggle) {
            thinkCombo.setItems(ThinkValueSupport.toggleItems().toArray(String[]::new));
        } else if (form instanceof ThinkSupport.Values v) {
            thinkCombo.setItems(ThinkValueSupport.valuesItems(v).toArray(String[]::new));
        }
        setThinkVisible(thinkLabel, combo || text);
        setThinkVisible(thinkCombo, combo);
        setThinkVisible(thinkText, text);
        applyThinkValue(value, carried);
        parent.layout();
    }

    private static void setThinkVisible(Control control, boolean visible) {
        ((GridData) control.getLayoutData()).exclude = !visible; // GridLayout honors only exclude
        control.setVisible(visible);
    }

    private Combo newCombo() {
        var combo = new Combo(parent, SWT.BORDER);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return combo;
    }

    private void applyThinkValue(String value, boolean carried) {
        if (thinkForm instanceof ThinkSupport.Toggle) {
            // editable combo, stored value = displayed value
            thinkCombo.setText(StringUtil.stripToEmpty(value));
        } else if (thinkForm instanceof ThinkSupport.Values) {
            var display = ThinkValueSupport.valuesDisplay(value);
            var idx = thinkCombo.indexOf(display);
            if (idx >= 0) thinkCombo.select(idx);
            else if (carried) thinkCombo.setText(""); // fixed list, no match → unset (never a silent replacement)
            else thinkCombo.setText(display); // explicit load: unknown value shown verbatim
        } else if (thinkForm instanceof ThinkSupport.FreeString || thinkForm instanceof ThinkSupport.Unknown) {
            thinkText.setText(StringUtil.stripToEmpty(value)); // verbatim
        }
        // ThinkSupport.None → no field (value dropped)
    }

    private String readThink() {
        if (thinkForm instanceof ThinkSupport.Values) return ThinkValueSupport.valuesStored(thinkCombo.getText());
        if (thinkForm instanceof ThinkSupport.Toggle) return StringUtil.stripToEmpty(thinkCombo.getText());
        if (thinkForm instanceof ThinkSupport.FreeString || thinkForm instanceof ThinkSupport.Unknown) {
            return StringUtil.stripToEmpty(thinkText.getText());
        }
        return ""; // None
    }
}
