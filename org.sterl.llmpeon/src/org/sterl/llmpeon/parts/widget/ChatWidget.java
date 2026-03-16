package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.sterl.llmpeon.ChatService;
import org.sterl.llmpeon.agent.PeonMode;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.parts.shared.EclipseTemplateContext;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFilesTool;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget.SimpleChatMessage;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;

public class ChatWidget extends Composite implements EclipseAiMonitor {

    private static final NullProgressMonitor NULL_MONITOR = new NullProgressMonitor();
    private final ChatService<EclipseTemplateContext> chatService;
    private ChatMarkdownWidget chatHistory;
    private StatusLineWidget statusLine;
    private Text inputArea;
    private Button btnSend;
    private Button btnCompress;
    private Button btnClear;
    private Button btnImplement;
    private Combo modeCombo;
    private Combo modelCombo;

    private boolean working = false;
    private boolean noModelConfigured = false;
    private LlmConfig lastListedConfig;

    private final AgentsMdService agentsMdService = new AgentsMdService();
    
    private final AtomicReference<IProgressMonitor> monitorRef = new AtomicReference<>();

    @Override
    public boolean setFocus() {
        if (inputArea != null)
            return inputArea.setFocus();
        return super.setFocus();
    }

    public ChatWidget(ChatService<EclipseTemplateContext> chatService, Composite parent, int style) {
        super(parent, style);
        this.chatService = chatService;
        createLayout();
        loadModelsInBackground(chatService.getConfig());
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
                    doSendMessage();
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
        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.wrap = true;
        rowLayout.pack = true;
        rowLayout.center = true;
        rowLayout.marginTop = 2;
        rowLayout.marginBottom = 2;
        bar.setLayout(rowLayout);

        modeCombo = new Combo(bar, SWT.READ_ONLY);
        modeCombo.setItems("Peon-Plan", "Peon-Dev");
        modeCombo.select(1); // default: Peon-Dev
        modeCombo.setToolTipText("Select agent mode");
        modeCombo.addListener(SWT.Selection, e -> {
            PeonMode selected = modeCombo.getSelectionIndex() == 0 ? PeonMode.PLAN : PeonMode.DEV;
            chatService.setMode(selected);
            refreshChat();
        });

        modelCombo = new Combo(bar, SWT.READ_ONLY);
        modelCombo.setLayoutData(new RowData(200, SWT.DEFAULT));
        modelCombo.setToolTipText("Select model (fetched from provider)");
        modelCombo.addListener(SWT.Selection, e -> {
            String selected = modelCombo.getText();
            if (selected != null && !selected.isBlank()) {
                chatService.updateConfig(chatService.getConfig().withModel(selected));
            }
        });

        btnSend = new Button(bar, SWT.PUSH);
        btnSend.setImage(org.eclipse.debug.ui.DebugUITools.getImage(
                org.eclipse.debug.ui.IDebugUIConstants.IMG_ACT_RUN));
        btnSend.setToolTipText("Send...");
        btnSend.addListener(SWT.Selection, e -> doSendMessage());

        btnCompress = new Button(bar, SWT.PUSH);
        btnCompress.setText("Compress");
        btnCompress.setToolTipText("Compress conversation context");
        btnCompress.addListener(SWT.Selection, e -> doCompressContext());

        btnClear = new Button(bar, SWT.PUSH);
        btnClear.setText("Clear");
        btnClear.setToolTipText("Clear conversation context");
        btnClear.addListener(SWT.Selection, e -> {
            chatService.clear();
            chatHistory.clear();
/*
            Job.create("findJavaType for ChatService", monitor -> {
                var tool = new EclipseCodeNavigationTool();
                var result = new EclipseCodeNavigationTool().findJavaType("ChatService", getUserSelection());
                Display.getDefault().asyncExec(() -> inputArea.setText(result));
                
                Display.getDefault().asyncExec(() -> {
                    this.chatHistory.appendMessage(new SimpleChatMessage("AI", tool.getTypeSource("org.sterl.llmpeon.ChatService", null)));
                });
                

                monitor.done();
                return Status.OK_STATUS;
            }).schedule();
*/
        });

        btnImplement = new Button(bar, SWT.PUSH);
        btnImplement.setText("Start Impl.");
        RowData rdImpl = new RowData();
        rdImpl.exclude = true;
        btnImplement.setLayoutData(rdImpl);
        btnImplement.setVisible(false);
        btnImplement.setEnabled(false);
        btnImplement.setToolTipText("Switch to Dev mode and start implementing the plan");
        btnImplement.addListener(SWT.Selection, e -> {
            modeCombo.select(1);
            chatService.setMode(PeonMode.DEV);
            refreshChat();
            inputArea.setText("Start Implementation");
            doSendMessage();
        });
    }

    private void updateModeUI() {
        boolean isPlan = chatService.getMode() == PeonMode.PLAN;
        if (isPlan) {
            boolean hasAiMessage = chatService.getMessages().stream()
                    .anyMatch(m -> m.type() == ChatMessageType.AI);
            btnImplement.setEnabled(hasAiMessage);
        }
        if (btnImplement.getVisible() != isPlan) {
            ((RowData) btnImplement.getLayoutData()).exclude = !isPlan;
            btnImplement.setVisible(isPlan);
            btnImplement.getParent().layout();
        }
    }

    /**
     * Single method to refresh the status line.
     * Call after config changes, file selection changes, or token updates.
     */
    public void refreshStatusLine() {
        statusLine.update(
            chatService.getTokenSize(),
            chatService.getTokenWindow(),
            chatService.getSkills().size(),
            agentsMdService.hasAgentFile(),
            chatService.getTemplateContext().getSelectedResource()
        );
    }

    public void onResourceSelected() {
        var resource = chatService.getTemplateContext().getSelectedResource();
        if (resource != null) {
            agentsMdService.load(resource.getProject());
        }
        refreshStatusLine();
    }

    private void refreshChat() {
        chatHistory.clearMessages();
        chatService.getMessages().forEach(msg -> {
            chatHistory.appendMessage(msg);
        });
        refreshStatusLine();
        updateModeUI();
    }

    void lockWhileWorking(boolean value) {
        this.working = value;
        btnCompress.setEnabled(!this.working);
        btnSend.setEnabled(!this.working && !noModelConfigured);
        btnClear.setEnabled(!this.working);
        btnImplement.setEnabled(!this.working);
    }

    /** Called when the active config changes (e.g. after Preferences save). */
    public void onConfigChanged(LlmConfig config) {
        if (lastListedConfig == null
                || config.providerType() != lastListedConfig.providerType()
                || !java.util.Objects.equals(config.url(), lastListedConfig.url())
                || !java.util.Objects.equals(config.apiKey(), lastListedConfig.apiKey())) {
            loadModelsInBackground(config);
        } else {
            // provider unchanged — just update the selection
            Display.getDefault().asyncExec(() -> {
                if (isDisposed()) return;
                selectModel(config.model());
            });
        }
    }

    private void loadModelsInBackground(LlmConfig config) {
        Job.create("Fetching available models", monitor -> {
            var models = config.providerType().listModels(config);
            lastListedConfig = config;
            Display.getDefault().asyncExec(() -> {
                if (isDisposed()) return;
                applyModelList(models, config.model());
            });
            return org.eclipse.core.runtime.Status.OK_STATUS;
        }).schedule();
    }

    private void applyModelList(java.util.List<String> models, String configuredModel) {
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

    private void selectModel(String model) {
        if (model == null || model.isBlank()) {
            modelCombo.select(0);
        } else {
            int idx = -1;
            String[] items = modelCombo.getItems();
            for (int i = 0; i < items.length; i++) {
                if (items[i].equals(model)) { idx = i; break; }
            }
            if (idx >= 0) {
                modelCombo.select(idx);
            } else {
                // configured model not in list — use first and sync service
                modelCombo.select(0);
                if (items.length > 0) {
                    chatService.updateConfig(chatService.getConfig().withModel(items[0]));
                }
            }
        }
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
    public void onProblem(String value) {
        Display.getDefault().asyncExec(() -> {
            chatHistory.appendMessage(new SimpleChatMessage("PROBLEM", "`" + value + "`"));
        });
    }
    
    @Override
    public void onFileUpdate(AiFileUpdate update) {
        var diff = SimpleDiff.unifiedDiff(update.file(), update.oldContent(), update.newContent());
        Display.getDefault().asyncExec(() -> chatHistory.showDiff(diff));
    }
    
    private void doCompressContext() {
        lockWhileWorking(true);
        chatHistory.clear();
        Job.create("Compressing context", monitor -> {
            monitor.beginTask("Compressing chat", IProgressMonitor.UNKNOWN);
            monitorRef.set(monitor);
            Exception ex = null;
            
            try {
                chatService.compressContext(this);
                Display.getDefault().asyncExec(this::refreshChat);
            } catch (Exception e) {
                ex = e;
            } finally {
                monitorRef.set(NULL_MONITOR);
                Display.getDefault().asyncExec(() -> lockWhileWorking(false));
            }
            monitor.done();
            return ex == null ? Status.OK_STATUS : new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, ex.getMessage(), ex);
        }).schedule();
    }

    private void doSendMessage() {
        String text = StringUtil.strip(inputArea.getText().trim() + getUserSelection());
        if (StringUtil.hasNoValue(text) && chatService.getMessages().isEmpty()) return;
        if (StringUtil.hasValue(text)) chatHistory.appendMessage(new SimpleChatMessage(ChatMessageType.USER.name(), text));
        inputArea.setText("");
        
        lockWhileWorking(true);
        Job.create("Peon AI request", monitor -> {
            monitor.beginTask("Arbeit, Arbeit!", IProgressMonitor.UNKNOWN);
            monitorRef.set(monitor);

            Exception ex = null;
            try {
                int msgCountBefore = chatService.getMessages().size();
                chatService.setStandingOrders(buildStandingOrders());
                var result = chatService.call(text.isEmpty() ? null : text, this);

                Display.getDefault().asyncExec(() -> {
                    if (chatService.getMessages().size() < msgCountBefore) {
                        refreshChat();
                    } else {
                        chatHistory.appendMessage(result.aiMessage());
                        refreshStatusLine();
                        updateModeUI();
                    }
                });
            } catch (Exception e) {
                ex = e;
                Display.getDefault().asyncExec(() -> chatHistory.appendMessage(
                        new SimpleChatMessage("PROBLEM", e.getMessage())));
            } finally {
                Display.getDefault().asyncExec(() -> lockWhileWorking(false));
                chatService.setStandingOrders(Collections.emptyList());
                monitorRef.set(NULL_MONITOR);
                monitor.done();
            }

            return PeonConstants.status("Peon AI\n" + chatService.getConfig(), ex);
        }).schedule();
    }

    // TODO this is very messy from the AI and need a refactoring ...
    private List<ChatMessage> buildStandingOrders() {
        var templateContext = chatService.getTemplateContext();
        var selectedResource = templateContext.getSelectedResource();
        if (selectedResource != null) {
            agentsMdService.load(selectedResource.getProject());
        }
        var orders = new ArrayList<ChatMessage>();
        if (selectedResource != null) {
            orders.add(SystemMessage.from("Selected eclipse resource: " + JdtUtil.pathOf(selectedResource)));
        }
        agentsMdService.agentMessage(templateContext).ifPresent(orders::add);
        return orders;
    }

    private String getUserSelection() {
        var textSelection = chatService.getTemplateContext().getTextSelection();
        String userIn = "";
        if (textSelection != null && StringUtil.hasValue(textSelection.getText())) {
            userIn += "\n\nSelected:\n\n" + textSelection.getText();
            userIn += "\n\nStart line: " + textSelection.getStartLine();
            Optional<? extends IResource> file = EclipseUtil.getOpenFile();
            if (file.isEmpty()) file = Optional.ofNullable(chatService.getTemplateContext().getSelectedResource());
            if (file.isPresent()) userIn += "\n\nEclipse resource use " 
                    + EclipseWorkspaceReadFilesTool.READ_ECLIPSE_FILE_TOOL + " to read the whole file if needed: " 
                    + JdtUtil.pathOf(file.get());
        }
        return userIn;
    }

    public void append(String who, String what) {
        chatHistory.appendMessage(new SimpleChatMessage(who, what));
    }

    @Override
    public IProgressMonitor getIProgressMonitor() {
        return IProgressMonitor.nullSafe(monitorRef.get());
    }
    
    @Override
    public boolean isCanceled() {
        return monitorRef.get() == null ? false : monitorRef.get().isCanceled();
    }
}

