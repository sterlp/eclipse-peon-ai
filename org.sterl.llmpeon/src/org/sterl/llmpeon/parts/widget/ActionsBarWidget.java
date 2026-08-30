package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Action bar below the user input. RowLayout (wrapping) with agent selector,
 * Clear, and conditional controls.
 */
public class ActionsBarWidget extends Composite {

    private Button btnClear;
    private Button btnImplement;
    private final Button btnCompact;
    private Combo agentCombo;

    private volatile boolean working = false;
    private List<AiAgent> agents = new ArrayList<>();
    
    private final Color colorWarning;
    private final Color colorError;


    public ActionsBarWidget(Composite parent, int style,
            Runnable onClear,
            Runnable onImplement,
            Consumer<AiAgent> onAgentChange,
            Runnable onCompress) {
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

        btnCompact = new Button(this, SWT.PUSH);
        buildCompact(onCompress);

        btnClear = new Button(this, SWT.PUSH);
        //btnClear.setText("Clear");
        btnClear.setImage(ImageUtil.loadImage(this, ImageUtil.CLEAR));
        btnClear.setToolTipText("Clear conversation context");
        btnClear.addListener(SWT.Selection, e -> onClear.run());
        
        buildBtnImplement(onImplement);
    }
    
    private void buildCompact(Runnable onCompress) {
        btnCompact.setImage(ImageUtil.loadImage(this, ImageUtil.COMPACT));
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

    private void buildAgentCombo(Consumer<AiAgent> onModeChange) {
        agentCombo = new Combo(this, SWT.READ_ONLY);
        agentCombo.setLayoutData(new RowData(120, SWT.DEFAULT));
        rebuildAgentItems();
        agentCombo.select(0);
        agentCombo.setToolTipText("Select an Agent");
        agentCombo.addListener(SWT.Selection, e -> {
            var agent = this.agents.get(agentCombo.getSelectionIndex());
            onModeChange.accept(agent);
            applyImplAutonomousVisibility(agent.handoverTo());
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

    /** Enable/disable controls while a request is in flight. */
    public void lockWhileWorking(boolean value) {
        working = value;
        agentCombo.setEnabled(!working);
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
        applyImplAutonomousVisibility(agent.handoverTo());
    }

    private void applyImplAutonomousVisibility(String handOver) {
        boolean hashandOver = handOver != null;
        if (hashandOver) {
            btnImplement.setText("Handoff → " + handOver);
            btnImplement.setToolTipText("Handover the plan or last AI message to " + handOver);
            btnImplement.setEnabled(true);
        } else {
            btnImplement.setEnabled(false);
        }

        if (btnImplement.getVisible() != hashandOver) {
            ((RowData) btnImplement.getLayoutData()).exclude = !hashandOver;
            btnImplement.setVisible(hashandOver);
            layout(true, true);
            getParent().layout(new Control[]{this});
        }
    }
}
