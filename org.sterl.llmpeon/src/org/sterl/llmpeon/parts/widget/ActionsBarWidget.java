package org.sterl.llmpeon.parts.widget;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.ai.model.AiModel;

/**
 * Action bar below the user input. RowLayout (wrapping) with mode selector,
 * model selector, Think toggle, Clear, and conditional controls.
 */
public class ActionsBarWidget extends Composite {

    private Button btnClear;
    private Button btnImplement;
    private Button chkAutonomous;
    private Button btnThink;
    private Combo agentCombo;
    private Combo modelCombo;

    private final AtomicBoolean working = new AtomicBoolean(false);
    private boolean agentModeAvailable = false;
    private List<AiModel> availableModels = List.of();

    public ActionsBarWidget(Composite parent, int style,
            Runnable onClear,
            Runnable onImplement,
            Consumer<PeonMode> onModeChange,
            Consumer<AiModel> onModelChange,
            Consumer<Boolean> onAutonomousChange,
            Consumer<Boolean> onThinkToggle) {
        super(parent, style);

        setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.wrap = true;
        rowLayout.pack = true;
        rowLayout.center = true;
        rowLayout.marginTop = 2;
        rowLayout.marginBottom = 2;
        rowLayout.marginLeft = 4;
        rowLayout.marginRight = 4;
        rowLayout.spacing = 4;
        setLayout(rowLayout);

        buildAgentCombo(onModeChange);

        buildModelCombo(onModelChange);

        btnThink = new Button(this, SWT.TOGGLE);
        btnThink.setText("\uD83E\uDDE0 Think");
        btnThink.setToolTipText("Enable extended thinking for the next request");
        btnThink.addListener(SWT.Selection, e -> onThinkToggle.accept(btnThink.getSelection()));

        btnClear = new Button(this, SWT.PUSH);
        btnClear.setText("Clear");
        btnClear.setToolTipText("Clear conversation context");
        btnClear.addListener(SWT.Selection, e -> onClear.run());

        buildBtnImplement(onImplement);
        buildChkAutonomous(onAutonomousChange);
    }

	private void buildBtnImplement(Runnable onImplement) {
		btnImplement = new Button(this, SWT.PUSH);
        btnImplement.setText("Start Impl.");
        RowData rdImpl = new RowData();
        rdImpl.exclude = true;
        btnImplement.setLayoutData(rdImpl);
        btnImplement.setVisible(false);
        btnImplement.setEnabled(false);
        btnImplement.setToolTipText("Switch to Dev mode and start implementing the plan");
        btnImplement.addListener(SWT.Selection, e -> onImplement.run());
	}

	private void buildChkAutonomous(Consumer<Boolean> onAutonomousChange) {
		chkAutonomous = new Button(this, SWT.CHECK);
        chkAutonomous.setText("autonomous");
        chkAutonomous.setToolTipText("Automatically start implementation after the plan is saved");
        RowData rdAuto = new RowData();
        rdAuto.exclude = true;
        chkAutonomous.setLayoutData(rdAuto);
        chkAutonomous.setVisible(false);
        chkAutonomous.addListener(SWT.Selection, e -> onAutonomousChange.accept(chkAutonomous.getSelection()));
	}

	private void buildModelCombo(Consumer<AiModel> onModelChange) {
		modelCombo = new Combo(this, SWT.READ_ONLY);
        modelCombo.setLayoutData(new RowData(150, SWT.DEFAULT));
        modelCombo.setToolTipText("Select model (fetched from provider)");
        modelCombo.addListener(SWT.Selection, e -> {
            int idx = modelCombo.getSelectionIndex();
            if (idx >= 0 && idx < availableModels.size()) {
                onModelChange.accept(availableModels.get(idx));
            }
        });
	}

	private void buildAgentCombo(Consumer<PeonMode> onModeChange) {
		agentCombo = new Combo(this, SWT.READ_ONLY);
        agentCombo.setLayoutData(new RowData(100, SWT.DEFAULT));
        agentCombo.setItems(Arrays.asList(PeonMode.values()).stream()
                .map(PeonMode::getLabel)
                .toArray(String[]::new));
        agentCombo.select(1); // default: Peon-Dev
        agentCombo.setToolTipText("Select agent mode");
        agentCombo.addListener(SWT.Selection, e -> {
            PeonMode selected = PeonMode.values()[agentCombo.getSelectionIndex()];
            if (selected == PeonMode.AGENT && !agentModeAvailable) {
                agentCombo.select(PeonMode.DEV.ordinal());
                agentCombo.setToolTipText("Peon-Agent requires a project to be selected");
                return;
            }
            agentCombo.setToolTipText("Select agent mode");
            onModeChange.accept(selected);
        });
	}

    /** Enable/disable controls while a request is in flight. */
    public void lockWhileWorking(boolean value) {
        this.working.set(value);
        agentCombo.setEnabled(!value);
        modelCombo.setEnabled(!value);
        btnClear.setEnabled(!value);
        btnThink.setEnabled(!value);
        if (value) btnImplement.setEnabled(false); // re-enable is handled by updateModeUI
    }

    public boolean isWorking() {
        return this.working.get();
    }

    /** Show/hide the "Start Impl." button based on mode and whether an AI reply exists. */
    public void updateModeUI(PeonMode mode, boolean implEnabled) {
        boolean isPlanLike = mode == PeonMode.PLAN || mode == PeonMode.AGENT;
        boolean isAgent = mode == PeonMode.AGENT;
        agentCombo.select(mode.ordinal());
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
        agentCombo.setToolTipText(available ? "Select agent mode"
                : "Peon-Agent requires a project to be selected");
    }

    /** Set the autonomous checkbox state without firing the listener. */
    public void setAutonomous(boolean value) {
        chkAutonomous.setSelection(value);
    }

    /** Set the Think toggle state without firing the listener. */
    public void setThinkEnabled(boolean value) {
        btnThink.setSelection(value);
    }

    /** Returns whether the Think toggle is currently on. */
    public boolean isThinkEnabled() {
        return btnThink.getSelection();
    }

    /** Populate the model combo with the available models. */
    public void applyModelList(List<AiModel> models, String selectedModelId) {
        availableModels = models;
        modelCombo.setEnabled(true);
        modelCombo.setItems(models.stream().map(AiModel::getName).toArray(String[]::new));
        selectModel(selectedModelId);
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
}
