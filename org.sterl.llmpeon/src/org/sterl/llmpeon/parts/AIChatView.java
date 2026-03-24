package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
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
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkingSet;
import org.sterl.llmpeon.ChatService;
import org.sterl.llmpeon.agent.AgentService;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.PeonMode;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.agent.AgentModeService;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.tools.AgentModeTools;
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
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class AIChatView implements EclipseAiMonitor {

    @Inject
    Logger logger;

    private final TemplateContext context = new TemplateContext(Path.of("./"));
    private ChatService<TemplateContext> chatService;
    private AgentService agentService;
    private PeonMode currentMode = PeonMode.DEV;
    private final ToolService toolService = new ToolService();
    private final SkillService skillService = new SkillService();
    private final EclipseWorkspaceWriteFilesTool workspaceWriteFilesTool = new EclipseWorkspaceWriteFilesTool();
    private final EclipseWorkspaceReadFilesTool workspaceReadFilesTool = new EclipseWorkspaceReadFilesTool();
    private final AgentsMdService agentsMdService = new AgentsMdService();
    private final AtomicReference<IProgressMonitor> monitorRef = new AtomicReference<>(new NullProgressMonitor());
    private AgentModeService agentMode;
    private AgentModeTools agentModeTools;
    private LlmConfig lastListedConfig;
    private IProject currentProject;
    private boolean projectPinned = false;

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
        agentService = new AgentService(chatService.getChatModel());

        agentMode = new AgentModeService(chatService, this::doSendMessage);
        agentModeTools = new AgentModeTools(agentMode);

        chatHistory = new ChatMarkdownWidget(parent, SWT.BORDER);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        chatInput = new ChatWidget(parent, SWT.NONE, this::doSendMessage);
        chatInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLine = new StatusLineWidget(parent, SWT.NONE, this::onPinChange);

        actionsBar = new ActionsBarWidget(parent, SWT.NONE,
            this::doSendMessage,
            () ->  getIProgressMonitor().setCanceled(true),
            this::doCompressContext,
            () -> { chatService.clear(); chatHistory.clear(); },
            this::doStartImpl,
            this::onModeChange,
            model -> { chatService.updateConfig(chatService.getConfig().withModel(model)); agentService.updateModel(chatService.getChatModel()); },
            autonomous -> agentMode.setAutonomous(autonomous)
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
        } else if (o instanceof IProject p) {
            selection = p;
        } else if (o instanceof IFolder f) {
            selection = f;
        } else if (o instanceof IJavaProject jp) {
            selection = jp.getResource();
        } else if (o instanceof IWorkingSet) {
            // nothing
            selection = selectedResource; 
        } else if (o != null) {
            System.err.println("!!! Unknown resource type selected " + o.getClass());
            selection = null;
        } else {
            selection = null;
        }
        selectedResource = selection;
        if (selectedResource != null) {
            System.out.println("Selected " + selectedResource.getName());
        }

        var project = selection != null ? EclipseUtil.resolveProject(selection) : null;
        if (project != null && !projectPinned) {
            currentProject = project;
            agentsMdService.load(selection.getProject());
            workspaceWriteFilesTool.setCurrentProject(project);
            workspaceReadFilesTool.setCurrentProject(project);
            agentMode.setProject(project);
        }

        if (chatInput != null) Display.getDefault().asyncExec(() -> {
            actionsBar.setAgentModeAvailable(currentProject != null && currentProject.isOpen());
            if (currentProject == null && currentMode == PeonMode.AGENT) {
                onModeChange(PeonMode.DEV);
            }
            refreshStatusLine();
        });
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object[] selectedObjects) {
        // TODO what to do with multi-selection?
        if (selectedObjects != null && selectedObjects.length > 0) {
            setSelection(selectedObjects[0]);
        }
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
            chatService.getSkills().size(),
            agentsMdService.hasAgentFile(),
            currentProject,
            getSelectedFile()
        );
        actionsBar.updateCompact(chatService.getTokenSize(), chatService.getTokenWindow());
    }

    private void refreshChat() {
        chatHistory.clearMessages();
        chatService.getMessages().forEach(chatHistory::appendMessage);
        refreshStatusLine();
        actionsBar.updateModeUI(currentMode, isImplEnabled());
    }

    private boolean isImplEnabled() {
        return switch (currentMode) {
            case PLAN  -> chatService.getMessages().stream().anyMatch(m -> m.type() == ChatMessageType.AI);
            case AGENT -> agentMode.overviewExists();
            default    -> false;
        };
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
        if (agentService != null) agentService.updateModel(chatService.getChatModel());
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
                // proper review needed
                String[] items = actionsBar.getModelItems();
                boolean inList = StringUtil.hasValue(config.model())
                        && java.util.Arrays.stream(items).anyMatch(config.model()::equals);
                if (!inList) {
                    loadModelsInBackground(config);
                } else {
                    actionsBar.selectModel(config.model());
                }
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
                        agentService.updateModel(chatService.getChatModel());
                    }
                }
            });
            return Status.OK_STATUS;
        }).schedule();
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void onModeChange(PeonMode mode) {
        if (mode == PeonMode.AGENT) {
            toolService.addTool(agentModeTools);
            agentMode.reset();
            agentMode.setAutonomous(true);
            actionsBar.setAutonomous(true);
            if (agentMode.overviewExists()) {
                chatService.addMessage(UserMessage.from(
                        "Existing plan found:\n\n" + agentMode.readOverview()));
                agentMode.openOverviewInEditor();
                refreshChat();
            }
        } else {
            // Safe to call even if not currently registered
            try { toolService.removeTool(agentModeTools); } catch (Exception ignored) {}
            agentMode.reset();
        }
        currentMode = mode;
        actionsBar.updateModeUI(currentMode, isImplEnabled());
        refreshStatusLine();
    }

    private void doStartImpl() {
        if (currentMode == PeonMode.AGENT) {
            agentMode.startImplementation();
            refreshChat();
            return;
        }

        // Original PLAN→DEV logic
        currentMode = PeonMode.DEV;
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

        if (chatService.getMessages().isEmpty()) return;
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
        if (StringUtil.hasNoValue(chatService.getConfig().model())) {
            chatHistory.appendMessage(new SimpleChatMessage("PROBLEM", "No model configured — open Window > Preferences > Peon AI"));
            return;
        }
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
                var agent = buildAgent();
                var result = chatService.call(agent, text.isEmpty() ? null : text, this);

                Display.getDefault().asyncExec(() -> {
                    if (parent.isDisposed()) return;
                    if (agentMode.consumeImplementationRequest()) {
                        agentMode.startImplementation();
                        refreshChat();
                    } else if (chatService.getMessages().size() < msgCountBefore) {
                        refreshChat();
                    } else {
                        chatHistory.appendMessage(result.aiMessage());
                        refreshStatusLine();
                        actionsBar.updateModeUI(currentMode, isImplEnabled());
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

    private void onPinChange(boolean pinned) {
        this.projectPinned = pinned;
        if (!pinned && selectedResource != null) {
            var project = EclipseUtil.resolveProject(selectedResource);
            if (project != null) {
                currentProject = project;
                agentsMdService.load(selectedResource.getProject());
                workspaceWriteFilesTool.setCurrentProject(project);
                workspaceReadFilesTool.setCurrentProject(project);
                agentMode.setProject(project);
            }
            actionsBar.setAgentModeAvailable(currentProject != null && currentProject.isOpen());
        }
        Display.getDefault().asyncExec(() -> {
            statusLine.setPinned(pinned);
            refreshStatusLine();
        });
    }

    private AiAgent buildAgent() {
        var ctx = chatService.getTemplateContext();
        return switch (currentMode) {
            case PLAN  -> agentService.newPlannerAgent(ctx);
            case DEV   -> agentService.newDeveloperAgent(ctx);
            case AGENT -> agentMode.currentAgentIsPlanning()
                          ? agentService.newPlannerAgent(ctx, true)
                          : agentService.newDeveloperAgent(ctx, true);
        };
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
        if (currentMode == PeonMode.AGENT) {
            var hint = agentMode.planPathHint();
            if (!hint.isBlank()) orders.add(SystemMessage.from(hint));
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
