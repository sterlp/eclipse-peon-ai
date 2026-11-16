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
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Model selector for the chat action bar: editable combo + refresh button. Delegates the model-list
 * lifecycle (fetch, cache, stale-guard) to {@link ModelComboWidget}. Owns the RowLayout look and
 * commit logic (see {@code docs/model-loading.md} R-ML4, R-ML5).
 *
 * <p>SWT: {@code SWT.Selection} fires only for user interaction, never for
 * {@code select()}/{@code setText()}/{@code setItems()}. FocusOut/Selection order is
 * platform-dependent — the caller must {@link #commitPending()} before an agent switch.</p>
 */
public class ChatModelComboWidget {

    private final Consumer<String> onModelSelected;
    private final Combo combo;
    private final Button refresh;
    private final ModelComboWidget loader;
    /** The model last shown by {@link #show} (or committed) — the baseline for "real change". */
    private String shownModel;

    public ChatModelComboWidget(Composite parent, int width, Supplier<FetchSnapshot> snapshot, Consumer<String> onModelSelected) {
        this.onModelSelected = onModelSelected;

        combo = new Combo(parent, SWT.BORDER);
        combo.setLayoutData(new RowData(width, SWT.DEFAULT));
        combo.addListener(SWT.Selection, e -> commit());
        combo.addListener(SWT.FocusOut, e -> commit());

        refresh = new Button(parent, SWT.PUSH);
        refresh.setImage(ImageUtil.loadImage(refresh, ImageUtil.Icon.REFRESH));
        refresh.setToolTipText("Reload the model list for this agent's connection");

        loader = new ModelComboWidget(combo, refresh, "chat", snapshot);
    }

    private void commit() {
        var text = StringUtil.stripToEmpty(combo.getText());
        if (text.equalsIgnoreCase(StringUtil.stripToEmpty(shownModel))) return;
        shownModel = text;
        onModelSelected.accept(text);
    }

    public void commitPending() {
        if (!combo.isDisposed()) commit();
    }

    public void show(AgentModelSelection selection) {
        setModel(selection.effectiveModel());
        combo.setToolTipText(tooltipFor(selection));
    }

    public void setModel(String model) {
        shownModel = model;
        loader.setModel(model);
    }

    public String getModel() {
        return loader.getModel();
    }

    public void setEnabled(boolean enabled) {
        combo.setEnabled(enabled);
        refresh.setEnabled(enabled);
    }

    public void fetchModels() {
        loader.fetchModels();
    }

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
