package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.sterl.llmpeon.agent.AgentModelSelection;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget.FetchSnapshot;
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Action bar below the user input: RowLayout (wrapping) with agent selector, model selector,
 * Clear, and conditional controls. Model selection delegates to {@link ChatModelComboWidget}
 * (see {@code docs/model-loading.md} R-ML5).
 */
public class ActionsBarWidget extends Composite {

    public static final int COMBO_WIDTH = 120;

    private final Button btnClear;
    private Button btnImplement;
    private final Button btnCompact;
    private final Button btnSync;
    private Combo agentCombo;
    private final ChatModelComboWidget modelCombo;

    private volatile boolean working = false;
    private boolean modelSelectable = true;
    private final List<AiAgent> agents = new ArrayList<>();
    
    private final Color colorWarning;
    private final Color colorError;


    public ActionsBarWidget(Composite parent, int style,
            Runnable onClear,
            Runnable onImplement,
            Consumer<AiAgent> onAgentChange,
            Supplier<FetchSnapshot> modelSnapshot,
            Consumer<String> onModelChange,
            Runnable onCompress,
            Runnable onReload) {
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
        rowLayout.marginLeft = 4;
        rowLayout.marginRight = 4;
        rowLayout.spacing = 4;
        setLayout(rowLayout);

        buildAgentCombo(onAgentChange);
        modelCombo = new ChatModelComboWidget(this, COMBO_WIDTH, modelSnapshot, onModelChange);

        btnCompact = new Button(this, SWT.PUSH);
        buildCompact(onCompress);
        
        btnSync = new Button(this, SWT.PUSH);
        buildBtnSync(onReload);

        btnClear = new Button(this, SWT.PUSH);
        btnClear.setImage(ImageUtil.loadImage(this, ImageUtil.CLEAR));
        btnClear.setToolTipText("Clear conversation context");
        btnClear.addListener(SWT.Selection, e -> onClear.run());
        
        buildBtnImplement(onImplement);
    }
    
    private void buildCompact(Runnable onCompress) {
        btnCompact.setImage(ImageUtil.loadImage(this, ImageUtil.Icon.COMPACT));
        btnCompact.setToolTipText("Compact conversation context");
        btnCompact.addListener(SWT.Selection, e -> onCompress.run());
    }

    private void buildBtnImplement(Runnable onImplement) {
        btnImplement = new Button(this, SWT.PUSH);
        btnImplement.setText("Start Impl.");
        RowData rdImpl = new RowData();
        rdImpl.exclude = true;
        btnImplement.setLayoutData(rdImpl);
        btnImplement.setVisible(false);
        btnImplement.setEnabled(true);
        btnImplement.addListener(SWT.Selection, e -> onImplement.run());
    }
    
    private void buildBtnSync(Runnable onReload) {
        btnSync.setImage(ImageUtil.loadImage(this, ImageUtil.Icon.REFRESH));
        btnSync.setToolTipText("Reload chat");
        btnSync.addListener(SWT.Selection, e -> onReload.run());
    }

    private void buildAgentCombo(Consumer<AiAgent> onAgentChange) {
        agentCombo = new Combo(this, SWT.READ_ONLY);
        agentCombo.setLayoutData(new RowData(COMBO_WIDTH, SWT.DEFAULT));
        rebuildAgentItems();
        agentCombo.select(0);
        agentCombo.setToolTipText("Select an Agent");
        agentCombo.addListener(SWT.Selection, e -> {
            var agent = this.agents.get(agentCombo.getSelectionIndex());
            onAgentChange.accept(agent);
            updateHandoffVisibility(agent.handoverTo());
        });
    }

    private void rebuildAgentItems() {
        var items = new ArrayList<String>();
        for (var a : agents) items.add(a.getName());
        agentCombo.setItems(items.toArray(String[]::new));
    }
    
    /** Update the Compact button label and tooltip with current token usage. */
    public void updateCompact(int tokenUsed, int tokenMax) {
        if (tokenMax <= 0) tokenMax = 999999;

        int pct = tokenMax > 0 ? (tokenUsed * 100) / tokenMax : 0;
        if (pct >= 85) btnCompact.setForeground(colorError);
        else if (pct >= 70) btnCompact.setForeground(colorWarning);
        else btnCompact.setForeground(null);

        btnCompact.setText(StringUtil.toK(tokenUsed) + "/" + StringUtil.toK(tokenMax));
        btnCompact.setToolTipText(pct+ "% used, " 
                + StringUtil.toK(tokenUsed) + "/" + StringUtil.toK(tokenMax) + " — click to compact the conversation");
        if (tokenUsed < 1000) {
            btnCompact.setEnabled(false);
        } else {
            btnCompact.setEnabled(!working);
            btnCompact.getParent().layout(false, false);
        }

    }

    /** Replaces the custom agents in the combo, preserving the current selection by label. */
    public void setAgents(List<AiAgent> agents) {
        this.agents.clear();
        this.agents.addAll(agents);
        String previous = agentCombo.getText();
        rebuildAgentItems();
        String[] items = agentCombo.getItems();
        int restore = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(previous)) { restore = i; break; }
        }
        agentCombo.select(restore);
    }

    public void showModel(AgentModelSelection selection) {
        modelCombo.show(selection);
        modelSelectable = selection.selectable();
        modelCombo.setEnabled(!working && modelSelectable);
    }

    public void commitPendingModel() {
        modelCombo.commitPending();
    }

    public void fetchModels() {
        modelCombo.fetchModels();
    }

    /** Enable/disable controls while a request is in flight. */
    public void lockWhileWorking(boolean value) {
        working = value;
        agentCombo.setEnabled(!working);
        modelCombo.setEnabled(!working && modelSelectable);
        btnClear.setEnabled(!working);
        btnImplement.setEnabled(!working);
        btnCompact.setEnabled(!working);
    }

    public boolean isWorking() {
        return working;
    }

    /** Show/hide the "Start Impl." button based on mode and whether an AI reply exists. */
    public void updateModeUI(AiAgent agent) {
        var index= this.agents.indexOf(agent);
        agentCombo.select(index);
        updateHandoffVisibility(agent.handoverTo());
    }

    private void updateHandoffVisibility(String handoffTarget) {
        boolean hasHandoffTarget = handoffTarget != null;
        if (hasHandoffTarget) {
            btnImplement.setText("Handoff → " + handoffTarget);
            btnImplement.setToolTipText("Handover the plan or last AI message to " + handoffTarget);
            btnImplement.setEnabled(true);
        } else {
            btnImplement.setEnabled(false);
        }

        if (btnImplement.getVisible() != hasHandoffTarget) {
            ((RowData) btnImplement.getLayoutData()).exclude = !hasHandoffTarget;
            btnImplement.setVisible(hasHandoffTarget);
            layout(true, true);
            getParent().layout(new Control[]{this});
        }
    }
}
