package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.sterl.llmpeon.ChatService;
import org.sterl.llmpeon.agent.PeonMode;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFilesTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFilesTool;
import org.sterl.llmpeon.parts.widget.ActionsBarWidget;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget.SimpleChatMessage;
import org.sterl.llmpeon.parts.widget.ChatWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.DiskFileReadTools;
import org.sterl.llmpeon.tool.DiskFileWriteTools;
import org.sterl.llmpeon.tool.EditTool;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class AIChatView implements EclipseAiMonitor {

    @Inject
    Logger logger;

    private final TemplateContext context = new TemplateContext(Path.of("./"));
    private ChatService<TemplateContext> chatService;
    private final ToolService toolService = new ToolService();
    private final SkillService skillService = new SkillService();
    private final EclipseWorkspaceWriteFilesTool workspaceWriteFilesTool = new EclipseWorkspaceWriteFilesTool();
    private final EclipseWorkspaceReadFilesTool workspaceReadFilesTool = new EclipseWorkspaceReadFilesTool();
    private final AgentsMdService agentsMdService = new AgentsMdService();
    private final AtomicReference<IProgressMonitor> monitorRef = new AtomicReference<>(new NullProgressMonitor());
    private LlmConfig lastListedConfig;

    private ChatMarkdownWidget chatHistory;
    private ChatWidget chatInput;
    private StatusLineWidget statusLine;
    private ActionsBarWidget actionsBar;
    private Composite parent;
    
    private ITextSelection textSelection;
    private IResource selectedResource;

    private final IPreferenceChangeListener prefListener = event -> {
        if (parent != null && !parent.isDisposed()) {
            parent.getDisplay().asyncExec(this::applyConfig);
        }
    };

    @PostConstruct
    public void createPartControl(Composite parent) {
        this.parent = parent;
        parent.setLayout(new GridLayout(1, false));

        var rootPaht = ResourcesPlugin.getWorkspace().getRoot().getRawLocation().toFile().toPath();
        toolService.addTool(workspaceWriteFilesTool);
        toolService.addTool(workspaceReadFilesTool);
        toolService.addTool(new DiskFileWriteTools(rootPaht));
        toolService.addTool(new DiskFileReadTools(rootPaht));
        toolService.addTool(new EditTool(rootPaht));
        toolService.addTool(new EclipseBuildTool());
        toolService.addTool(new EclipseGrepTool());
        toolService.addTool(new EclipseRunTestTool());
        toolService.addTool(new EclipseCodeNavigationTool());

        chatService = new ChatService<>(LlmPreferenceInitializer.buildWithDefaults(), toolService, skillService, context);

        chatHistory = new ChatMarkdownWidget(parent, SWT.BORDER);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        chatInput = new ChatWidget(parent, SWT.NONE, this::doSendMessage);
        chatInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLine = new StatusLineWidget(parent, SWT.NONE);

        actionsBar = new ActionsBarWidget(parent, SWT.NONE,
            this::doSendMessage,
            () ->  getIProgressMonitor().setCanceled(true),
            this::doCompressContext,
            () -> { chatService.clear(); chatHistory.clear(); },
            this::doStartImpl,
            mode -> { chatService.setMode(mode); refreshStatusLine(); },
            model -> chatService.updateConfig(chatService.getConfig().withModel(model))
        );

        applyConfig();

        if (logger != null)
            logger.info("AIChatView started: " + chatService.getConfig());

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);
    }

    @PreDestroy
    public void dispose() {
        InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID).removePreferenceChangeListener(prefListener);
    }

    @Focus
    public void setFocus() {
        if (chatInput != null) chatInput.setFocus();
    }

    // -------------------------------------------------------------------------
    // Eclipse selection injection
    // -------------------------------------------------------------------------

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) ISelection s) {
        if (s == null || s.isEmpty()) return;
        if (s instanceof IStructuredSelection iss) {
            if (iss.size() == 1) setSelection(iss.getFirstElement());
            else setSelection(iss.toArray());
        }
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void setTextSelection(@Named(IServiceConstants.ACTIVE_SELECTION) ITextSelection ts) {
        textSelection = ts;
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object o) {
        if (o instanceof ISelection) return;

        final IResource selection;
        if (o instanceof ICompilationUnit cu) {
            selection = cu.getResource();
        } else if (o instanceof IFile f) {
            selection = f;
        } else if (o instanceof IResource r) {
            selection = r;
        } else if (o != null) {
            System.out.println("Unknown selected type " + o.getClass());
            selection = null;
        } else {
            selection = null;
        }
        selectedResource = selection;

        if (selection != null) {
            agentsMdService.load(selection.getProject());
            workspaceWriteFilesTool.setCurrentProject(EclipseUtil.resolveProject(selection));
            workspaceReadFilesTool.setCurrentProject(EclipseUtil.resolveProject(selection));
        }

        if (chatInput != null) Display.getDefault().asyncExec(this::refreshStatusLine);
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object[] selectedObjects) {
        // TODO what to do with multi-selection?
    }

    // -------------------------------------------------------------------------
    // EclipseAiMonitor
    // -------------------------------------------------------------------------

    @Override
    public void onAction(String value) {
        Display.getDefault().asyncExec(() ->
            chatHistory.appendMessage(new SimpleChatMessage("TOOL", "`" + value + "`")));
    }

    @Override
    public void onThink(String value) {
        Display.getDefault().asyncExec(() ->
            chatHistory.appendMessage(new SimpleChatMessage("THINK", "`" + value + "`")));
    }

    @Override
    public void onProblem(String value) {
        Display.getDefault().asyncExec(() ->
            chatHistory.appendMessage(new SimpleChatMessage("PROBLEM", "`" + value + "`")));
    }

    @Override
    public void onFileUpdate(AiFileUpdate update) {
        var diff = SimpleDiff.unifiedDiff(update.file(), update.oldContent(), update.newContent());
        Display.getDefault().asyncExec(() -> chatHistory.showDiff(diff));
    }

    @Override
    public IProgressMonitor getIProgressMonitor() {
        return IProgressMonitor.nullSafe(monitorRef.get());
    }

    @Override
    public boolean isCanceled() {
        return getIProgressMonitor().isCanceled();
    }

    // -------------------------------------------------------------------------
    // UI refresh
    // -------------------------------------------------------------------------

    public void refreshStatusLine() {
        statusLine.update(
            chatService.getTokenSize(),
            chatService.getTokenWindow(),
            chatService.getSkills().size(),
            agentsMdService.hasAgentFile(),
            getSelectedFile()
        );
    }

    private void refreshChat() {
        chatHistory.clearMessages();
        chatService.getMessages().forEach(chatHistory::appendMessage);
        refreshStatusLine();
        actionsBar.updateModeUI(chatService.getMode(),
            chatService.getMessages().stream().anyMatch(m -> m.type() == ChatMessageType.AI));
    }

    // -------------------------------------------------------------------------
    // Config / model loading
    // -------------------------------------------------------------------------

    private void applyConfig() {
        var config = LlmPreferenceInitializer.buildWithDefaults();
        try {
            skillService.refresh(config.skillDirectory());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + config.skillDirectory());
        }
        chatService.updateConfig(config);
        refreshStatusLine();
        onConfigChanged(config);
    }

    private void onConfigChanged(LlmConfig config) {
        if (lastListedConfig == null
                || config.providerType() != lastListedConfig.providerType()
                || !java.util.Objects.equals(config.url(), lastListedConfig.url())
                || !java.util.Objects.equals(config.apiKey(), lastListedConfig.apiKey())) {
            loadModelsInBackground(config);
        } else {
            Display.getDefault().asyncExec(() -> {
                if (parent.isDisposed()) return;
                actionsBar.selectModel(config.model());
            });
        }
    }

    private void loadModelsInBackground(LlmConfig config) {
        Job.create("Fetching available models", monitor -> {
            var models = config.providerType().listModels(config);
            lastListedConfig = config;
            Display.getDefault().asyncExec(() -> {
                if (parent.isDisposed()) return;
                actionsBar.applyModelList(models, config.model());
                // if configured model wasn't in list, sync service to first available
                if (!models.isEmpty() && !models.contains(config.model())) {
                    String[] items = actionsBar.getModelItems();
                    if (items.length > 0) {
                        chatService.updateConfig(config.withModel(items[0]));
                    }
                }
            });
            return Status.OK_STATUS;
        }).schedule();
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void doStartImpl() {
        chatService.setMode(PeonMode.DEV);
        actionsBar.updateModeUI(PeonMode.DEV, true);

        boolean tooLarge = chatService.getMessages().size() > 4
                && chatService.getTokenSize() > (chatService.getTokenWindow() * 0.4);

        AiMessage plan = null;
        if (tooLarge) {
            var messages = chatService.getMessages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof AiMessage ai && StringUtil.hasValue(ai.text())) {
                    plan = ai;
                    break;
                }
            }
        }

        if (plan != null) chatService.clear();
        chatService.addMessage(UserMessage.from("Start Implementation"));
        if (plan != null) chatService.addMessage(plan);

        refreshChat();
        doSendMessage();
    }

    private void doCompressContext() {
        chatHistory.clear();
        actionsBar.lockWhileWorking(true);
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
                monitorRef.set(new NullProgressMonitor());
                Display.getDefault().asyncExec(() -> actionsBar.lockWhileWorking(false));
            }
            monitor.done();
            return ex == null ? Status.OK_STATUS
                : new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, ex.getMessage(), ex);
        }).schedule();
    }

    private void doSendMessage() {
        String text = StringUtil.strip(chatInput.getText().trim() + getUserSelection());
        if (StringUtil.hasNoValue(text) && chatService.getMessages().isEmpty()) return;

        if (StringUtil.hasValue(text)) {
            chatHistory.appendMessage(new SimpleChatMessage(ChatMessageType.USER.name(), text));
            chatInput.clearText();
        }

        actionsBar.lockWhileWorking(true);
        Job.create("Peon AI request", monitor -> {
            monitor.beginTask("Arbeit, Arbeit!", IProgressMonitor.UNKNOWN);
            monitorRef.set(monitor);
            Exception ex = null;
            try {
                int msgCountBefore = chatService.getMessages().size();
                chatService.setStandingOrders(buildStandingOrders());
                var result = chatService.call(text.isEmpty() ? null : text, this);

                Display.getDefault().asyncExec(() -> {
                    if (parent.isDisposed()) return;
                    if (chatService.getMessages().size() < msgCountBefore) {
                        refreshChat();
                    } else {
                        chatHistory.appendMessage(result.aiMessage());
                        refreshStatusLine();
                        actionsBar.updateModeUI(chatService.getMode(),
                            chatService.getMessages().stream().anyMatch(m -> m.type() == ChatMessageType.AI));
                    }
                });
            } catch (Exception e) {
                ex = e;
                Display.getDefault().asyncExec(() ->
                    chatHistory.appendMessage(new SimpleChatMessage("PROBLEM", e.getMessage())));
            } finally {
                Display.getDefault().asyncExec(() -> actionsBar.lockWhileWorking(false));
                chatService.setStandingOrders(Collections.emptyList());
                monitorRef.set(new NullProgressMonitor());
                monitor.done();
            }
            return PeonConstants.status("Peon AI\n" + chatService.getConfig(), ex);
        }).schedule();
    }

    // TODO this is very messy from the AI and needs a refactoring ...
    private List<ChatMessage> buildStandingOrders() {
        var templateContext = chatService.getTemplateContext();
        var orders = new ArrayList<ChatMessage>();
        if (selectedResource != null) {
            orders.add(SystemMessage.from("Selected resource: " + JdtUtil.pathOf(selectedResource)));
        }
        if (agentsMdService.hasAgentFile()) {
            agentsMdService.agentMessage(templateContext).ifPresent(orders::add);
        }
        return orders;
    }

    private String getUserSelection() {
        if (textSelection == null || StringUtil.hasNoValue(textSelection.getText())) return "";
        var file = getSelectedFile();

        var extentsion = "\n";
        if (file != null) extentsion = file.getFileExtension() + "\n";

        String userIn = "\n\nSelected:\n```" + extentsion + textSelection.getText() + "\n```";
        userIn += "\n\nStart line: " + (textSelection.getStartLine() + 1);

        if (file != null) userIn += "\nFile: " + JdtUtil.pathOf(file) + " use "
                + EclipseWorkspaceReadFilesTool.READ_ECLIPSE_FILE_TOOL + " to read whole file, if needed.";
        return userIn;
    }
    
    private IFile getSelectedFile() {
        if (selectedResource instanceof IFile rf) return rf;
        var open = EclipseUtil.getOpenFile();
        if (open.isPresent()) return open.get();
        return null;
    }
}
