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
import org.eclipse.swt.widgets.Text;
import org.sterl.llmpeon.parts.llm.ChatService;
import org.sterl.llmpeon.parts.llm.LlmObserver;
import org.sterl.llmpeon.parts.tools.ToolService;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget.SimpleChatMessage;

import dev.langchain4j.data.message.ChatMessageType;

public class ChatWidget extends Composite implements LlmObserver {

    private final ChatService chatService;
    private ChatMarkdownWidget chatHistory;
    private Text inputArea;
    private Label tokenLabel;
    private Button send;
    private Button compress;

    @Override
    public boolean setFocus() {
        if (inputArea != null)
            return inputArea.setFocus();
        return super.setFocus();
    }

    public ChatWidget(ChatService chatService, ToolService toolService, Composite parent, int style) {
        super(parent, style);
        this.chatService = chatService;
        createLayout();
        chatService.addObserver(this);
        toolService.getContext().setDiffObserver(diff -> {
            Display.getDefault().asyncExec(() -> chatHistory.showDiff(diff));
        });

        addDisposeListener(e -> chatService.removeObserver(this));
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
        send.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        send.setToolTipText("Send...");
        send.addListener(SWT.Selection, e -> sendMessage());

        compress = new Button(bar, SWT.PUSH);
        compress.setText("Compress");
        compress.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        compress.setToolTipText("Compress conversation context");
        compress.addListener(SWT.Selection, e -> compressContext());

        tokenLabel = new Label(bar, SWT.NONE);
        tokenLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        updateTokenLabel();
    }

    private void updateTokenLabel() {
        int used = chatService.getLastInputTokens();
        int max = chatService.getTokenWindow();
        int pct = max > 0 ? (used * 100) / max : 0;
        tokenLabel.setText(used + " / " + max + " - " + pct + "%");
        tokenLabel.getParent().layout();
    }

    private void refreshChat() {
        chatHistory.clear();
        chatService.getMessages().forEach(msg -> {
            chatHistory.appendMessage(msg);
        });
        updateTokenLabel();
    }

    private void compressContext() {
        compress.setEnabled(false);
        send.setEnabled(false);
        Job.create("Compressing context", monitor -> {
            monitor.beginTask("Compressing", IProgressMonitor.UNKNOWN);
            Exception ex = null;
            try {
                String summary = chatService.compressContext();
                Display.getDefault().asyncExec(() -> {
                    refreshChat();
                    compress.setEnabled(true);
                    send.setEnabled(true);
                });
            } catch (Exception e) {
                ex = e;
                Display.getDefault().asyncExec(() -> {
                    compress.setEnabled(true);
                    send.setEnabled(true);
                });
            }
            monitor.done();
            return ex == null ? Status.OK_STATUS : new Status(IStatus.ERROR, "AIChat", ex.getMessage(), ex);
        }).schedule();
    }

    public void onAction(String value) {
        Display.getDefault().asyncExec(() -> {
            chatHistory.appendMessage(new SimpleChatMessage("Tool", "`" + value + "`"));
        });
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() && chatService.getMessages().isEmpty()) return;
        inputArea.setText("");
        send.setEnabled(false);
        if (text.length() > 0) chatHistory.appendMessage(
                new SimpleChatMessage(ChatMessageType.USER.name(), text));

        Job.create("LLM request", monitor -> {
            monitor.beginTask("Calling LLM", IProgressMonitor.UNKNOWN);

            Exception ex = null;
            try {
                int msgCountBefore = chatService.getMessages().size();
                var result = chatService.sendMessage(text);

                Display.getDefault().asyncExec(() -> {
                    // if auto-compress happened, memory was reset — refresh entire chat
                    if (chatService.getMessages().size() < msgCountBefore) {
                        refreshChat();
                    } else {
                        chatHistory.appendMessage(result.aiMessage());
                        updateTokenLabel();
                    }
                    send.setEnabled(true);
                });
            } catch (Exception e) {
                ex = e;
                Display.getDefault().asyncExec(() -> send.setEnabled(true));
            }

            monitor.done();
            return ex == null ? Status.OK_STATUS : new Status(IStatus.ERROR, "AIChat", ex.getMessage(), ex);
        }).schedule();
    }

    public void append(String who, String what) {
        chatHistory.appendMessage(new SimpleChatMessage(who, what));
    }
}
