package org.sterl.llmpeon.parts.widget;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget;

/**
 * Base-model selector for the chat dialog's action bar (next to the agent combo): an editable
 * {@link Combo} (R-ML4 — typed text stays, no auto-switch) plus a refresh button.
 *
 * <p>Shows and switches the <b>base</b> model ("one model per server"); per-agent overrides
 * from the advanced config page stay effective — the tooltip says so. Loading the list
 * (fetch Job, cache, refresh fallback, stale-guard, R-ML4 apply) is delegated to
 * {@link ModelComboWidget} — this class only owns the RowLayout look and the commit listeners.
 * The {@code liveConfig} supplier is read on the UI thread when a fetch starts and for the
 * stale-guard (R-ML1: the identity must reflect the settings at fetch time).</p>
 *
 * <p>A user selection or a focus-out commits the shown text to the caller, which persists it
 * (PREF_MODEL) through the existing config path — settings and chat stay in sync. Programmatic
 * updates ({@link #setModel}, list apply) do not commit: SWT fires {@code SWT.Selection} only
 * for user interaction, never for {@code select()}/{@code setText()}/{@code setItems()}.</p>
 */
public class ChatModelComboWidget {

    private final Consumer<String> onModelSelected;
    private final Combo combo;
    private final Button refresh;
    private final ModelComboWidget loader;

    public ChatModelComboWidget(Composite parent, Supplier<LlmConfig> liveConfig, Consumer<String> onModelSelected) {
        this.onModelSelected = onModelSelected;

        combo = new Combo(parent, SWT.BORDER);
        combo.setLayoutData(new RowData(200, SWT.DEFAULT));
        combo.setToolTipText("Base model for this connection — per-agent overrides from the Peon AI settings stay effective");
        combo.addListener(SWT.Selection, e -> commit());
        combo.addListener(SWT.FocusOut, e -> commit());

        refresh = new Button(parent, SWT.PUSH);
        refresh.setText("↻");
        refresh.setToolTipText("Reload the model list for this connection");

        loader = new ModelComboWidget(combo, refresh, "chat",
                () -> ModelComboWidget.baseSnapshot(liveConfig.get()));
    }

    /** Commits the shown text (user selection or focus-out). */
    private void commit() {
        onModelSelected.accept(combo.getText());
    }

    /** Sets the configured model (null shows as empty). */
    public void setModel(String model) {
        loader.setModel(model);
    }

    /** The currently shown model (may be the user's free text). */
    public String getModel() {
        return loader.getModel();
    }

    /** Enable/disable combo and refresh button (e.g. while a request is in flight). */
    public void setEnabled(boolean enabled) {
        combo.setEnabled(enabled);
        refresh.setEnabled(enabled);
    }

    /** Fetches the base model list for the current settings (view open). Cached per identity. */
    public void fetchModels() {
        loader.fetchModels();
    }
}
