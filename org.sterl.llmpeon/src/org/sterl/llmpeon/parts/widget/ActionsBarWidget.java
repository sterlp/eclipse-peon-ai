package org.sterl.llmpeon.parts.widget;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.parts.shared.ImageUtil;

public class ActionsBarWidget extends Composite {

    private Button btnSend;
    private Button btnStop;
    private Button btnCompress;
    private Button btnClear;
    private Button btnImplement;
    private Button chkAutonomous;
    private Button btnMcp;
    private Button btnMic;
    private Combo modeCombo;
    private Combo modelCombo;

    private final AtomicBoolean working = new AtomicBoolean(false);
    private boolean agentModeAvailable = false;
    private List<AiModel> availableModels = List.of();
    
    private final Color colorWarning;
    private final Color colorError;


    public ActionsBarWidget(Composite parent, int style,
            Runnable onSend,
            Runnable onStop,
            Runnable onCompress,
            Runnable onClear,
            Runnable onImplement,
            Consumer<PeonMode> onModeChange,
            Consumer<AiModel> onModelChange,
            Consumer<Boolean> onAutonomousChange,
            Consumer<Boolean> onMcpToggle,
            Runnable onMicClick) {
        super(parent, style);
        
        colorWarning = new Color(180, 130, 0);
        colorError = new Color(200, 0, 0);
        addDisposeListener(e -> {
            colorWarning.dispose();
            colorError.dispose();
        });

        setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.wrap = true;
        rowLayout.pack = true;
        rowLayout.center = true;
        rowLayout.marginTop = 2;
        rowLayout.marginBottom = 2;
        setLayout(rowLayout);

        modeCombo = new Combo(this, SWT.READ_ONLY);
        modeCombo.setItems(Arrays.asList(PeonMode.values()).stream()
                .map(PeonMode::getLabel)
                .toArray(String[]::new));
        modeCombo.select(1); // default: Peon-Dev
        modeCombo.setToolTipText("Select agent mode");
        modeCombo.addListener(SWT.Selection, e -> {
            PeonMode selected = PeonMode.values()[modeCombo.getSelectionIndex()];
            if (selected == PeonMode.AGENT && !agentModeAvailable) {
                modeCombo.select(PeonMode.DEV.ordinal());
                modeCombo.setToolTipText("Peon-Agent requires a project to be selected");
                return;
            }
            modeCombo.setToolTipText("Select agent mode");
            onModeChange.accept(selected);
        });

        modelCombo = new Combo(this, SWT.READ_ONLY);
        modelCombo.setLayoutData(new RowData(200, SWT.DEFAULT));
        modelCombo.setToolTipText("Select model (fetched from provider)");
        modelCombo.addListener(SWT.Selection, e -> {
            int idx = modelCombo.getSelectionIndex();
            if (idx >= 0 && idx < availableModels.size()) {
                onModelChange.accept(availableModels.get(idx));
            }
        });
        
        addMicButton(onMicClick);

        btnSend = new Button(this, SWT.PUSH);
        btnSend.setImage(DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN));
        btnSend.setToolTipText("Send...");
        btnSend.addListener(SWT.Selection, e -> onSend.run());

        btnStop = new Button(this, SWT.PUSH);
        btnStop.setToolTipText("Cancel current request");
        btnStop.setEnabled(false);
        btnStop.addListener(SWT.Selection, e -> onStop.run());
        btnStop.setImage(ImageUtil.loadImage(btnStop, ImageUtil.STOP));

        btnCompress = new Button(this, SWT.PUSH);
        btnCompress.setText("Compact");
        btnCompress.setToolTipText("Compact conversation context");
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
        btnImplement.addListener(SWT.Selection, e -> onImplement.run());

        chkAutonomous = new Button(this, SWT.CHECK);
        chkAutonomous.setText("autonomus");
        chkAutonomous.setToolTipText("Automatically start implementation after the plan is saved");
        RowData rdAuto = new RowData();
        rdAuto.exclude = true;
        chkAutonomous.setLayoutData(rdAuto);
        chkAutonomous.setVisible(false);
        chkAutonomous.addListener(SWT.Selection, e -> onAutonomousChange.accept(chkAutonomous.getSelection()));

        btnMcp = new Button(this, SWT.TOGGLE);
        btnMcp.setText("MCP");
        btnMcp.setToolTipText("Enable MCP tools (configure via Window > Preferences > AI Peon MCP)");
        btnMcp.addListener(SWT.Selection, e -> onMcpToggle.accept(btnMcp.getSelection()));

    }

    private void addMicButton(Runnable onMicClick) {
        btnMic = new Button(this, SWT.PUSH);
        btnMic.setImage(ImageUtil.loadImage(btnMic, ImageUtil.MICROPHONE));
        btnMic.setToolTipText("Click to start recording — click again to stop and transcribe");
        btnMic.addListener(SWT.Selection, e -> onMicClick.run());
        RowData rdMic = new RowData();
        rdMic.exclude = true;
        btnMic.setLayoutData(rdMic);
        btnMic.setVisible(false);
        btnMic.setBackground(colorError);
    }

    /** Update the Compact button label and tooltip with current token usage. */
    public void updateCompact(int tokenUsed, int tokenMax) {

        int pct = tokenMax > 0 ? (tokenUsed * 100) / tokenMax : 0;
        if (pct >= 70 ) btnCompress.setForeground(colorWarning);
        else if (pct >= 88) btnCompress.setForeground(colorError);
        else btnCompress.setForeground(null);

        btnCompress.setText("Compact " + pct + "%");
        btnCompress.setToolTipText(tokenUsed + " / " + tokenMax + " tokens used (" + pct
                + "%) — click to compact the conversation");
        btnCompress.getParent().layout(false, false);
        btnCompress.setEnabled(tokenUsed > 100 && !this.working.get());
    }

    /** Enable/disable the entire bar while a request is in flight. */
    public void lockWhileWorking(boolean value) {
        this.working.set(value);
        // Do NOT disable the parent composite — that would also block btnStop from receiving events.
        // Instead, disable each child individually.
        modeCombo.setEnabled(!value);
        modelCombo.setEnabled(!value);
        btnSend.setEnabled(!value);
        btnStop.setEnabled(value);
        btnCompress.setEnabled(!value);
        btnClear.setEnabled(!value);
        btnMcp.setEnabled(!value);
        if (value) btnImplement.setEnabled(false); // re-enable is handled by updateModeUI
    }
    
    public boolean isWorking() {
        return this.working.get();
    }

    /** Show/hide the "Start Impl." button based on mode and whether an AI reply exists. */
    public void updateModeUI(PeonMode mode, boolean implEnabled) {
        boolean isPlanLike = mode == PeonMode.PLAN || mode == PeonMode.AGENT;
        boolean isAgent = mode == PeonMode.AGENT;
        modeCombo.select(mode.ordinal());
        btnImplement.setEnabled(!this.working.get() && isPlanLike && implEnabled);
        boolean implVisibilityChanged = btnImplement.getVisible() != isPlanLike;
        if (implVisibilityChanged) {
            ((RowData) btnImplement.getLayoutData()).exclude = !isPlanLike;
            btnImplement.setVisible(isPlanLike);
        }
        boolean autoVisibilityChanged = chkAutonomous.getVisible() != isAgent;
        if (autoVisibilityChanged) {
            ((RowData) chkAutonomous.getLayoutData()).exclude = !isAgent;
            chkAutonomous.setVisible(isAgent);
        }
        if (implVisibilityChanged || autoVisibilityChanged) {
            layout(true, true);
            getParent().layout(new Control[]{this});
        }
    }

    /** Allow or block selection of Peon-Agent mode. */
    public void setAgentModeAvailable(boolean available) {
        this.agentModeAvailable = available;
        modeCombo.setToolTipText(available ? "Select agent mode"
                : "Peon-Agent requires a project to be selected");
    }

    /** Set the autonomous checkbox state without firing the listener. */
    public void setAutonomous(boolean value) {
        chkAutonomous.setSelection(value);
    }

    /** Enable or disable the MCP button (disabled when no servers are configured). */
    public void setMcpAvailable(boolean available) {
        btnMcp.setEnabled(available);
        if (!available) btnMcp.setSelection(false);
    }

    /** Set the MCP toggle button state without firing the listener. */
    public void setMcpEnabled(boolean value) {
        btnMcp.setSelection(value);
        if (value) btnMcp.setText("MCP on");
        else btnMcp.setText("MCP off");
    }

    /** Returns whether the MCP toggle is currently on. */
    public boolean isMcpEnabled() {
        return btnMcp.getSelection();
    }

    /** Populate the model combo with the available models. Combo displays names; IDs are tracked internally. */
    public void applyModelList(List<AiModel> models, String selectedModelId) {
        availableModels = models;
        modelCombo.setEnabled(true);
        modelCombo.setItems(models.stream().map(AiModel::getName).toArray(String[]::new));
        selectModel(selectedModelId);
        if (!this.working.get()) btnSend.setEnabled(true);
    }

    /** Select the model by its ID. Falls back to index 0 if not found. */
    public void selectModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            modelCombo.select(0);
            return;
        }
        for (int i = 0; i < availableModels.size(); i++) {
            if (availableModels.get(i).getId().equals(modelId)) {
                modelCombo.select(i);
                return;
            }
        }
        modelCombo.select(0);
    }

    /** Returns true if the given model ID is in the current list. */
    public boolean containsModelId(String modelId) {
        return availableModels.stream().anyMatch(m -> m.getId().equals(modelId));
    }

    /** Returns the ID of the currently selected model, or null if nothing is selected. */
    public String getSelectedModel() {
        int idx = modelCombo.getSelectionIndex();
        if (idx < 0 || idx >= availableModels.size()) return null;
        return availableModels.get(idx).getId();
    }

    /** Show or hide the microphone button based on voice config. */
    public void setVoiceInputVisible(boolean visible) {
        boolean changed = btnMic.getVisible() != visible;
        if (changed) {
            ((RowData) btnMic.getLayoutData()).exclude = !visible;
            btnMic.setVisible(visible);
            layout(true, true);
            getParent().layout(new Control[]{this});
        }
    }

    /** Toggle the mic button appearance between idle and recording states. */
    public void setRecording(boolean recording) {
        btnMic.setBackground(recording ? colorError : null);
        btnMic.setToolTipText(recording
            ? "Recording... click to stop and transcribe"
            : "Click to start recording — click again to stop and transcribe");
    }
}
