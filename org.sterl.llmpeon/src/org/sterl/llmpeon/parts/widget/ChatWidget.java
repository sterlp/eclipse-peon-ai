package org.sterl.llmpeon.parts.widget;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;
import org.sterl.llmpeon.parts.ChatService;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget.SimpleChatMessage;

public class ChatWidget extends Composite {

    private ChatService chatService;
    private ChatMarkdownWidget chatHistory;
    private Text inputArea;
    private ProgressBar tokenUsage;

    private Button send;

    @Override
    public boolean setFocus() {
        if (inputArea != null)
            return inputArea.setFocus();
        return super.setFocus();
    }

    public ChatWidget(ChatService chatService, Composite parent, int style) {
        super(parent, style);
        createLayout();
        this.chatService = chatService;
    }

    private void createLayout() {
        setLayout(new GridLayout(1, false));

        createChatHistory(this);
        createInputArea(this);
        createCommandBar(this);
    }

    // 1️ Chat history (top)
    private void createChatHistory(Composite parent) {
        chatHistory = new ChatMarkdownWidget(parent, SWT.BORDER);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    // 2️ Input area (middle, resizable)
    private void createInputArea(Composite parent) {
        inputArea = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.heightHint = 80;
        inputArea.setLayoutData(gd);

        inputArea.addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN) {
                boolean enter = (e.stateMask & SWT.CTRL) != 0 || (e.stateMask & SWT.COMMAND) != 0;

                if (enter) {
                    e.doit = false; // prevent newline
                    sendMessage();
                }
                // plain Enter → newline
            }
        });
    }

    // 3️ Command bar (bottom)
    private void createCommandBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        bar.setLayout(new GridLayout(3, false));

        send = new Button(bar, SWT.PUSH);
        send.setImage(org.eclipse.debug.ui.DebugUITools.getImage(
                org.eclipse.debug.ui.IDebugUIConstants.IMG_ACT_RUN));
        GridData gd = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        send.setLayoutData(gd);
        send.setToolTipText("Send...");

        tokenUsage = new ProgressBar(bar, SWT.NONE);
        tokenUsage.setMinimum(0);
        tokenUsage.setMaximum(100);
        tokenUsage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label tokenLabel = new Label(bar, SWT.NONE);
        tokenLabel.setText("0%");

        send.addListener(SWT.Selection, e -> sendMessage());
    }

    private void refreshChat() {
        chatHistory.clear();
        chatService.getMessages().forEach(msg -> {
            chatHistory.appendMessage(msg);
        });
    }

    
    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        inputArea.setText("");
        send.setEnabled(false);
        refreshChat();

        Job.create("LLM request", monitor -> {
            monitor.beginTask("Calling Ollama", IProgressMonitor.UNKNOWN);

            Exception ex = null;
            try {
                chatService.sendMessage(text);
            } catch (Exception e) {
                ex = e;
            }

            Display.getDefault().asyncExec(() -> {
                refreshChat();
                tokenUsage.setSelection(chatService.getMessages().size()); // placeholder for now
                send.setEnabled(true);
            });

            return ex == null ? Status.OK_STATUS : new Status(IStatus.ERROR, "AIChat", ex.getMessage(), ex);
        }).schedule();

        // TODO: async LLM call
        // tokenUsage.setSelection(25);
    }

    public void setChatService(ChatService chatService) {
        this.chatService = chatService;
    }

    public void append(String who, String what) {
        chatHistory.appendMessage(new SimpleChatMessage(who, what));
    }
}
