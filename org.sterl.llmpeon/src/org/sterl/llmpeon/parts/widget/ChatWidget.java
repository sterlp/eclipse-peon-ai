package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.ai.ChatService;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget.SimpleChatMessage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;

public class ChatWidget extends Composite implements AiMonitor {

    private final ChatService chatService;
    private ChatMarkdownWidget chatHistory;
    private StatusLineWidget statusLine;
    private Text inputArea;
    private Button btnSend;
    private Button btnCompress;

    private boolean working = false;

    private ITextSelection textSelection;
    private String selectedResource;
    private String agentsMdContent;

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
        statusLine = new StatusLineWidget(parent, SWT.NONE);
    }

    // 4 Command bar (bottom)
    private void createCommandBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        bar.setLayout(new GridLayout(2, false));

        btnSend = new Button(bar, SWT.PUSH);
        btnSend.setImage(org.eclipse.debug.ui.DebugUITools.getImage(
                org.eclipse.debug.ui.IDebugUIConstants.IMG_ACT_RUN));
        btnSend.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        btnSend.setToolTipText("Send...");
        btnSend.addListener(SWT.Selection, e -> sendMessage());

        btnCompress = new Button(bar, SWT.PUSH);
        btnCompress.setText("Compress");
        btnCompress.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        btnCompress.setToolTipText("Compress conversation context");
        btnCompress.addListener(SWT.Selection, e -> compressContext());
    }

    /**
     * Single method to refresh the status line.
     * Call after config changes, file selection changes, or token updates.
     */
    public void refreshStatusLine() {
        statusLine.update(
            chatService.getTokenSize(),
            chatService.getTokenWindow(),
            chatService.getToolService().getSkills().size(),
            agentsMdContent != null,
            selectedResource
        );
    }

    public void updateContextFile(String value) {
        this.selectedResource = value;
        this.agentsMdContent = null;

        var resource = EclipseUtil.resolveInEclipse(value);
        if (resource.isPresent()) {
            var project = resource.get().getProject();
            if (project != null && project.isOpen()) {
                IFile agentsFile = project.getFile("AGENTS.md");
                if (!agentsFile.exists()) agentsFile = project.getFile("agents.md");
                if (agentsFile.exists()) {
                    try (var in = agentsFile.getContents()) {
                        this.agentsMdContent = IoUtils.toString(in, agentsFile.getCharset());
                    } catch (Exception e) {
                        // ignore read failures
                    }
                }
            }
        }
        refreshStatusLine();
    }

    private void refreshChat() {
        chatHistory.clearMessages();
        chatService.getMessages().forEach(msg -> {
            chatHistory.appendMessage(msg);
        });
        refreshStatusLine();
    }

    void lockWhileWorking(boolean value) {
        this.working = value;
        btnCompress.setEnabled(!this.working);
        btnSend.setEnabled(!this.working);
    }

    private void compressContext() {
        lockWhileWorking(true);
        Job.create("Compressing context", monitor -> {
            monitor.beginTask("Compressing chat", IProgressMonitor.UNKNOWN);
            Exception ex = null;
            
            try {
                chatService.compressContext(this);
                Display.getDefault().asyncExec(this::refreshChat);
            } catch (Exception e) {
                ex = e;
            } finally {
                Display.getDefault().asyncExec(() -> lockWhileWorking(false));
            }
            monitor.done();
            return ex == null ? Status.OK_STATUS : new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, ex.getMessage(), ex);
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
                
                var orders = new ArrayList<ChatMessage>();
                if (selectedResource != null) {
                    orders.add(SystemMessage.from("Selected eclipse resource: " + selectedResource));
                }
                if (agentsMdContent != null) {
                    orders.add(SystemMessage.from(agentsMdContent));
                }
                if (!orders.isEmpty()) chatService.setStandingOrders(orders);
                var result = chatService.call(text + getUserSelection(), this);

                Display.getDefault().asyncExec(() -> {
                    if (chatService.getMessages().size() < msgCountBefore) {
                        refreshChat();
                    } else {
                        chatHistory.appendMessage(result.aiMessage());
                    }
                    refreshStatusLine();
                });
            } catch (Exception e) {
                ex = e;
            } finally {
                Display.getDefault().asyncExec(() -> lockWhileWorking(false));
                monitor.done();
                chatService.setStandingOrders(null);
            }

            return PeonConstants.status("Peon AI\n" + chatService.getConfig(), ex);
        }).schedule();
    }

    private String getUserSelection() {
        String userIn = "";
        if (textSelection != null && !textSelection.isEmpty()) {
            userIn += "\nSelected text:\n" + textSelection.getText();
            userIn += "\nStart line: " + textSelection.getStartLine();
            var file = EclipseUtil.getOpenFile();
            if (file.isPresent()) userIn += "\nFile: " + file.get().getFullPath().toPortableString();
        }
        return userIn;
    }

    public void append(String who, String what) {
        chatHistory.appendMessage(new SimpleChatMessage(who, what));
    }

    public void setTextSelection(ITextSelection ts) {
        this.textSelection = ts;
    }
}

