package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.sterl.llmpeon.ai.ConnectionIdentity;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Base-model selector for the chat dialog's action bar (next to the agent combo): an editable
 * {@link Combo} (R-ML4 — typed text stays, no auto-switch) plus a refresh button.
 *
 * <p>Shows and switches the <b>base</b> model ("one model per server"); per-agent overrides
 * from the advanced config page stay effective — the tooltip says so. The list comes from
 * {@link ModelListCache} for the base {@link ConnectionIdentity}: fetched on view open (or on
 * refresh), never while typing. A failed or empty list keeps the configured model — no error,
 * no auto-switch.</p>
 *
 * <p>A selection or a focus-out commits the shown text to the caller, which persists it
 * (PREF_MODEL) through the existing config path — settings and chat stay in sync. The
 * {@code liveConfig} supplier is read on the UI thread when a fetch starts and for the
 * stale-guard (R-ML1: the identity must reflect the settings at fetch time).</p>
 *
 * <p>Plain controller (no SWT parent of its own), mirroring {@link ModelComboWidget}: the
 * combo and refresh button are created directly in the given parent (RowLayout-compatible).</p>
 */
public class ChatModelComboWidget {

    private final Supplier<LlmConfig> liveConfig;
    private final Consumer<String> onModelSelected;
    private final Combo combo;
    private final Button refresh;

    /** Programmatic select() fires SWT.Selection — the guard suppresses the callback. */
    private boolean applying = false;

    public ChatModelComboWidget(Composite parent, Supplier<LlmConfig> liveConfig, Consumer<String> onModelSelected) {
        this.liveConfig = liveConfig;
        this.onModelSelected = onModelSelected;

        combo = new Combo(parent, SWT.BORDER);
        combo.setLayoutData(new RowData(200, SWT.DEFAULT));
        combo.setToolTipText("Base model for this connection — per-agent overrides from the Peon AI settings stay effective");
        combo.addListener(SWT.Selection, e -> commit());
        combo.addListener(SWT.FocusOut, e -> commit());

        refresh = new Button(parent, SWT.PUSH);
        refresh.setText("↻");
        refresh.setToolTipText("Reload the model list for this connection");
        refresh.addListener(SWT.Selection, e -> refreshModels());
    }

    /** Commits the shown text (selection or focus-out); programmatic updates are suppressed. */
    private void commit() {
        if (applying) return;
        onModelSelected.accept(combo.getText());
    }

    /** Sets the configured model (null shows as empty). */
    public void setModel(String model) {
        combo.setText(StringUtil.stripToEmpty(model));
    }

    /** The currently shown model (may be the user's free text). */
    public String getModel() {
        return combo.getText();
    }

    /** Enable/disable combo and refresh button (e.g. while a request is in flight). */
    public void setEnabled(boolean enabled) {
        combo.setEnabled(enabled);
        refresh.setEnabled(enabled);
    }

    // --- model list (per ConnectionIdentity, cached on success only) ---

    /** Fetches the base model list for the current settings (view open). Cached per identity. */
    public void fetchModels() {
        var snapshot = ModelComboWidget.baseSnapshot(liveConfig.get()); // UI thread: capture before the background Job
        Job.create("Loading chat models", monitor -> {
            var list = ModelListCache.instance().getOrFetch(snapshot.identity(), ModelComboWidget.fetchList(snapshot));
            applyList(list, snapshot.identity());
            return Status.OK_STATUS;
        }).schedule();
    }

    /** Manual refresh: always refetches; a failed fetch keeps the previous list. */
    private void refreshModels() {
        var snapshot = ModelComboWidget.baseSnapshot(liveConfig.get()); // UI thread: capture before the background Job
        Job.create("Refreshing chat models", monitor -> {
            var cache = ModelListCache.instance();
            var list = cache.refresh(snapshot.identity(), ModelComboWidget.fetchList(snapshot));
            applyList(list != null ? list : cache.cached(snapshot.identity()), snapshot.identity());
            return Status.OK_STATUS;
        }).schedule();
    }

    private void applyList(List<AiModel> fetched, ConnectionIdentity identity) {
        EclipseUtil.runInUiThread(combo, () -> {
            if (!identity.equals(ModelComboWidget.baseSnapshot(liveConfig.get()).identity())) return; // settings changed while fetching — stale
            applying = true;
            try {
                ModelComboWidget.applyModelListTo(combo, fetched);
            } finally {
                applying = false;
            }
        });
    }
}
