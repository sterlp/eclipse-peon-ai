package org.sterl.llmpeon.parts.widget;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.sterl.llmpeon.agent.PeonMode;

public class ActionsBarWidget extends Composite {

    private Button btnSend;
    private Button btnStop;
    private Button btnCompress;
    private Button btnClear;
    private Button btnImplement;
    private Combo modeCombo;
    private Combo modelCombo;

    private boolean working = false;
    private boolean noModelConfigured = false;

    public ActionsBarWidget(Composite parent, int style,
            Runnable onSend,
            Runnable onStop,
            Runnable onCompress,
            Runnable onClear,
            Runnable onImplement,
            Consumer<PeonMode> onModeChange,
            Consumer<String> onModelChange) {
        super(parent, style);
        setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.wrap = true;
        rowLayout.pack = true;
        rowLayout.center = true;
        rowLayout.marginTop = 2;
        rowLayout.marginBottom = 2;
        setLayout(rowLayout);

        modeCombo = new Combo(this, SWT.READ_ONLY);
        modeCombo.setItems("Peon-Plan", "Peon-Dev");
        modeCombo.select(1); // default: Peon-Dev
        modeCombo.setToolTipText("Select agent mode");
        modeCombo.addListener(SWT.Selection, e -> {
            PeonMode selected = modeCombo.getSelectionIndex() == 0 ? PeonMode.PLAN : PeonMode.DEV;
            onModeChange.accept(selected);
        });

        modelCombo = new Combo(this, SWT.READ_ONLY);
        modelCombo.setLayoutData(new RowData(200, SWT.DEFAULT));
        modelCombo.setToolTipText("Select model (fetched from provider)");
        modelCombo.addListener(SWT.Selection, e -> {
            String selected = modelCombo.getText();
            if (selected != null && !selected.isBlank()) {
                onModelChange.accept(selected);
            }
        });

        btnSend = new Button(this, SWT.PUSH);
        btnSend.setImage(DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN));
        btnSend.setToolTipText("Send...");
        btnSend.addListener(SWT.Selection, e -> onSend.run());

        btnStop = new Button(this, SWT.PUSH);
        btnStop.setToolTipText("Cancel current request");
        btnStop.setEnabled(false);
        btnStop.addListener(SWT.Selection, e -> onStop.run());
        try {
            Image stopImage = ImageDescriptor
                .createFromURL(URI.create("platform:/plugin/org.eclipse.jface/org/eclipse/jface/action/images/stop.svg").toURL())
                .createImage();
            btnStop.setImage(stopImage);
            btnStop.addDisposeListener(e -> stopImage.dispose());
        } catch (Exception ex) {
            btnStop.setText("Stop");
        }

        btnCompress = new Button(this, SWT.PUSH);
        btnCompress.setText("Compress");
        btnCompress.setToolTipText("Compress conversation context");
        btnCompress.addListener(SWT.Selection, e -> onCompress.run());

        btnClear = new Button(this, SWT.PUSH);
        btnClear.setText("Clear");
        btnClear.setToolTipText("Clear conversation context");
        btnClear.addListener(SWT.Selection, e -> onClear.run());

        btnImplement = new Button(this, SWT.PUSH);
        btnImplement.setText("Start Impl.");
        RowData rdImpl = new RowData();
        rdImpl.exclude = true;
        btnImplement.setLayoutData(rdImpl);
        btnImplement.setVisible(false);
        btnImplement.setEnabled(false);
        btnImplement.setToolTipText("Switch to Dev mode and start implementing the plan");
        btnImplement.addListener(SWT.Selection, e -> {
            modeCombo.select(1);
            onModeChange.accept(PeonMode.DEV);
            onImplement.run();
        });
    }

    /** Enable/disable the entire bar while a request is in flight. */
    public void lockWhileWorking(boolean value) {
        this.working = value;
        // Do NOT disable the parent composite — that would also block btnStop from receiving events.
        // Instead, disable each child individually.
        modeCombo.setEnabled(!value);
        modelCombo.setEnabled(!value);
        btnSend.setEnabled(!value && !noModelConfigured);
        btnStop.setEnabled(value);
        btnCompress.setEnabled(!value);
        btnClear.setEnabled(!value);
        if (value) btnImplement.setEnabled(false); // re-enable is handled by updateModeUI
    }

    /** Show/hide the "Start Impl." button based on mode and whether an AI reply exists. */
    public void updateModeUI(PeonMode mode, boolean hasAiMessage) {
        boolean isPlan = mode == PeonMode.PLAN;
        if (isPlan) {
            // lockWhileWorking(true) disables this; we re-enable here after the response arrives
            btnImplement.setEnabled(hasAiMessage);
        }
        if (btnImplement.getVisible() != isPlan) {
            ((RowData) btnImplement.getLayoutData()).exclude = !isPlan;
            btnImplement.setVisible(isPlan);
            layout();
        }
    }

    /** Populate the model combo. Disables send if no models are available. */
    public void applyModelList(List<String> models, String configuredModel) {
        if (models.isEmpty()) {
            noModelConfigured = true;
            modelCombo.setEnabled(false);
            modelCombo.setItems("No model — open Preferences");
            modelCombo.select(0);
            btnSend.setEnabled(false);
        } else {
            noModelConfigured = false;
            modelCombo.setEnabled(true);
            modelCombo.setItems(models.toArray(new String[0]));
            selectModel(configuredModel);
            if (!working) btnSend.setEnabled(true);
        }
    }

    /** Select the given model in the combo (no-op if not found — caller handles fallback). */
    public void selectModel(String model) {
        if (model == null || model.isBlank()) {
            modelCombo.select(0);
            return;
        }
        String[] items = modelCombo.getItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(model)) {
                modelCombo.select(i);
                return;
            }
        }
        modelCombo.select(0);
    }

    /** Returns the items currently listed in the model combo. */
    public String[] getModelItems() {
        return modelCombo.getItems();
    }
}
