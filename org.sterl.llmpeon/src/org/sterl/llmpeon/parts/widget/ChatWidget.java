package org.sterl.llmpeon.parts.widget;

import java.util.Arrays;

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
import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.ai.ChatService;
import org.sterl.llmpeon.parts.LlmPreferenceConstants;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget.SimpleChatMessage;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;

public class ChatWidget extends Composite implements AiMonitor {

    private final ChatService chatService;
    private ChatMarkdownWidget chatHistory;
    private Text inputArea;
    private Label statusLabel;
    private Button send;
    private Button compress;

    private boolean working = false;
    private String selectedResource;

    @Override
    public boolean setFocus() {
        if (inputArea != null)
            return inputArea.setFocus();
        return super.setFocus();
    }

    public ChatWidget(ChatService chatService, Composite parent, int style) {
        super(parent, style);
        this.chatService = chatService;
        createLayout();
    }

    private void createLayout() {
        setLayout(new GridLayout(1, false));

        createChatHistory(this);
        createInputArea(this);
        createStatusLine(this);
        createCommandBar(this);
    }

    // 1 Chat history (top)
    private void createChatHistory(Composite parent) {
        chatHistory = new ChatMarkdownWidget(parent, SWT.BORDER);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    // 2 Input area (middle)
    private void createInputArea(Composite parent) {
        inputArea = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.heightHint = 80;
        inputArea.setLayoutData(gd);

        inputArea.addTraverseListener(e -> {
            //if (working) return;
            if (e.detail == SWT.TRAVERSE_RETURN) {
                boolean enter = (e.stateMask & SWT.CTRL) != 0 || (e.stateMask & SWT.COMMAND) != 0;
                if (enter) {
                    e.doit = false;
                    sendMessage();
                }
            }
        });
    }

    // 3 Status line (above command bar)
    private void createStatusLine(Composite parent) {
        statusLabel = new Label(parent, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("");
    }

    // 4 Command bar (bottom)
    private void createCommandBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        bar.setLayout(new GridLayout(2, false));

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
    }

    /**
     * Single method to refresh the status line.
     * Call after config changes, file selection changes, or token updates.
     */
    public void refreshStatusLine() {
        int used = chatService.getTokenSize();
        int max = chatService.getTokenWindow();
        int pct = max > 0 ? (used * 100) / max : 0;

        int skillCount = chatService.getToolService().getSkills().size();

        String fileName = "";
        if (selectedResource != null && !selectedResource.isEmpty()) {
            int sep = selectedResource.lastIndexOf('/');
            if (sep < 0) sep = selectedResource.lastIndexOf('\\');
            fileName = sep >= 0 ? selectedResource.substring(sep + 1) : selectedResource;
        }

        var sb = new StringBuilder();
        sb.append(skillCount).append(" skill").append(skillCount != 1 ? "s" : "");
        if (!fileName.isEmpty()) sb.append(" | ").append(fileName);
        sb.append(" | ").append(used).append(" / ").append(max).append(" - ").append(pct).append("%");

        statusLabel.setText(sb.toString());
        statusLabel.getParent().layout();
    }

    public void updateContextFile(String value) {
        this.selectedResource = value;
        refreshStatusLine();
    }

    private void refreshChat() {
        chatHistory.clear();
        chatService.getMessages().forEach(msg -> {
            chatHistory.appendMessage(msg);
        });
        refreshStatusLine();
    }

    void lockWhileWorking(boolean value) {
        this.working = value;
        compress.setEnabled(!this.working);
        send.setEnabled(!this.working);
    }

    private void compressContext() {
        lockWhileWorking(true);
        Job.create("Compressing context", monitor -> {
            monitor.beginTask("Compressing", IProgressMonitor.UNKNOWN);
            Exception ex = null;
            
            try {
                chatService.compressContext(m -> {
                    Display.getDefault().asyncExec(() -> chatHistory.appendMessage(SimpleChatMessage.tool(m)));
                });
            } catch (Exception e) {
                ex = e;
            } finally {
                Display.getDefault().asyncExec(() -> lockWhileWorking(false));
            }
            monitor.done();
            return ex == null ? Status.OK_STATUS : new Status(IStatus.ERROR, LlmPreferenceConstants.PLUGIN_ID, ex.getMessage(), ex);
        }).schedule();
    }

    @Override
    public void onAction(String value) {
        Display.getDefault().asyncExec(() -> {
            chatHistory.appendMessage(new SimpleChatMessage("TOOL", "`" + value + "`"));
        });
    }
    
    @Override
    public void onThink(String value) {
        Display.getDefault().asyncExec(() -> {
            chatHistory.appendMessage(new SimpleChatMessage("THINK", "`" + value + "`"));
        });
    }
    
    @Override
    public void onFileUpdate(AiFileUpdate update) {
        var diff = SimpleDiff.unifiedDiff(update.file(), update.oldContent(), update.newContent());
        Display.getDefault().asyncExec(() -> chatHistory.showDiff(diff));
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() && chatService.getMessages().isEmpty()) return;
        inputArea.setText("");
        lockWhileWorking(true);
        if (text.length() > 0) chatHistory.appendMessage(new SimpleChatMessage(ChatMessageType.USER.name(), text));

        Job.create("Peon AI request", monitor -> {
            monitor.beginTask("Arbeit, Arbeit!", IProgressMonitor.UNKNOWN);

            Exception ex = null;
            try {
                int msgCountBefore = chatService.getMessages().size();
                
                if (selectedResource != null) {
                    chatService.setAdditionalChatMessages(Arrays.asList(SystemMessage.from("Selected eclipse resource: " + selectedResource)));
                }
                
                var result = chatService.call(text, this);

                Display.getDefault().asyncExec(() -> {
                    if (chatService.getMessages().size() < msgCountBefore) {
                        refreshChat();
                    } else {
                        chatHistory.appendMessage(result.aiMessage());
                        refreshStatusLine();
                    }
                    send.setEnabled(true);
                });
            } catch (Exception e) {
                ex = e;
            } finally {
                Display.getDefault().asyncExec(() -> lockWhileWorking(false));
                monitor.done();
                chatService.setAdditionalChatMessages(null);
            }

            return ex == null ? Status.OK_STATUS : new Status(IStatus.ERROR, "AIChat", ex.getMessage(), ex);
        }).schedule();
    }

    public void append(String who, String what) {
        chatHistory.appendMessage(new SimpleChatMessage(who, what));
    }
}
