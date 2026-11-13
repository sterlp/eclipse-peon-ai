package org.sterl.llmpeon.parts.widget;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.sterl.llmpeon.agent.AgentModelSelection;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget.FetchSnapshot;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Model selector for the chat dialog's action bar (next to the agent combo): an editable
 * {@link Combo} (R-ML4 — typed text stays, no auto-switch) plus a refresh button.
 *
 * <p>Shows and changes the <b>effective model of the active agent</b> (R-ML5,
 * {@code org.sterl.llmpeon/docs/model-loading.md}): the caller shows an
 * {@link AgentModelSelection} via {@link #show}, the tooltip states where a change goes. Loading
 * the list (fetch Job, cache, refresh fallback, stale-guard, R-ML4 apply) is delegated to
 * {@link ModelComboWidget} — this class only owns the RowLayout look and the commit. The
 * {@code snapshot} supplier is read on the UI thread when a fetch starts and for the stale-guard
 * (R-ML1) and resolves the active agent's connection.</p>
 *
 * <p>A user selection or a focus-out commits the shown text — but only if it really differs from
 * the shown model (case-insensitive, R-ML4), so leaving the field unchanged never saves.
 * Programmatic updates ({@link #show}, list apply) do not commit: SWT fires {@code SWT.Selection}
 * only for user interaction, never for {@code select()}/{@code setText()}/{@code setItems()}.
 * The caller flushes a pending edit with {@link #commitPending()} before it switches the agent
 * (R-ML5 BDD 10) — independent of the platform's FocusOut/Selection order.</p>
 */
public class ChatModelComboWidget {

    private final Consumer<String> onModelSelected;
    private final Combo combo;
    private final Button refresh;
    private final ModelComboWidget loader;
    /** The model last shown by {@link #show} (or committed) — the baseline for "real change". */
    private String shownModel;

    public ChatModelComboWidget(Composite parent, Supplier<FetchSnapshot> snapshot, Consumer<String> onModelSelected) {
        this.onModelSelected = onModelSelected;

        combo = new Combo(parent, SWT.BORDER);
        combo.setLayoutData(new RowData(200, SWT.DEFAULT));
        combo.addListener(SWT.Selection, e -> commit());
        combo.addListener(SWT.FocusOut, e -> commit());

        refresh = new Button(parent, SWT.PUSH);
        refresh.setText("↻");
        refresh.setToolTipText("Reload the model list for this agent's connection");

        loader = new ModelComboWidget(combo, refresh, "chat", snapshot);
    }

    /** Commits the shown text if it differs from the shown model (user selection or focus-out). */
    private void commit() {
        var text = StringUtil.stripToEmpty(combo.getText());
        if (text.equalsIgnoreCase(StringUtil.stripToEmpty(shownModel))) return;
        shownModel = text;
        onModelSelected.accept(text);
    }

    /** Flushes a typed but not yet committed model — call before the agent changes (R-ML5 BDD 10). */
    public void commitPending() {
        if (!combo.isDisposed()) commit();
    }

    /** Shows the selection of the active agent: its effective model and a tooltip naming the save target. */
    public void show(AgentModelSelection selection) {
        setModel(selection.effectiveModel());
        combo.setToolTipText(tooltipFor(selection));
    }

    /** Sets the shown model (null shows as empty) — never commits. */
    public void setModel(String model) {
        shownModel = model;
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

    /** Fetches the model list for the active agent's connection. Cached per identity. */
    public void fetchModels() {
        loader.fetchModels();
    }

    /** R-ML5: the tooltip states the effect of a change for the shown agent. */
    public static String tooltipFor(AgentModelSelection selection) {
        var name = selection.agentName();
        return switch (selection.target()) {
            case BASE -> "Base model — used by " + name + " and by every agent without its own model";
            case NONE -> name + " always uses the base model — change it with Dev or in the Peon AI settings";
            case AGENT_SLOT, AGENT_FILE -> {
                var where = selection.target() == AgentModelSelection.Target.AGENT_FILE ? " (saved in its AGENT.md)" : "";
                yield selection.inherited()
                        ? name + " inherits the base model — choosing a model sets " + name + "'s own model" + where
                        : name + "'s own model" + where + " — clear the field to inherit the base model again";
            }
        };
    }
}
