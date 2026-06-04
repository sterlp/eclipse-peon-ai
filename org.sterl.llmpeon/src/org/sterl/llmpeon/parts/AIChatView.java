package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkingSet;
import org.sterl.llmpeon.AbstractChatService;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpPreferenceInitializer;
import org.sterl.llmpeon.parts.config.VoicePreferenceInitializer;
import org.sterl.llmpeon.parts.model.UserContext;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.parts.widget.ActionsBarWidget;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget.SkillMenuSelection;
import org.sterl.llmpeon.parts.widget.UserInputWidget;
import org.sterl.llmpeon.parts.widget.UserQuestionWidget;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.voice.VoiceConfig;
import org.sterl.llmpeon.voice.VoiceInputService;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class AIChatView implements EclipseAiMonitor {

    private static final ILog LOG = Platform.getLog(AIChatView.class);

    // Declared first so the aiService field initializer lambdas can capture them
    // without violating the Java forward-reference restriction.
    // All are null until @PostConstruct runs; the lambdas are only ever invoked after that.
    private Composite parent;
    private ActionsBarWidget actionsBar;
    private StatusLineWidget statusLine;

    private final PeonAiService aiService = new PeonAiService(
        this::doSendMessage,
        file -> EclipseUtil.runInUiThread(parent, () -> EclipseUtil.openInEditor(file)),
        enabled -> EclipseUtil.runInUiThread(parent, () -> statusLine.setMcpEnabled(enabled))
    );

    private final AtomicReference<IProgressMonitor> monitorRef = new AtomicReference<>(new NullProgressMonitor());
    private final VoiceInputService voiceService = new VoiceInputService();
    private final WorkspaceMemoryTool workspaceMemoryTool = WorkspaceMemoryTool.getInstance();
    
    private volatile boolean recording = false;

    private AtomicReference<LlmConfig> lastListedConfig = new AtomicReference<>();
    private volatile LlmConfig lastAppliedConfig = null;

    private ChatMarkdownWidget chatHistory;
    private Composite inputBlock;
    private UserInputWidget chatInput;
    private UserQuestionWidget questionWidget;

    private final UserContext userContext = new UserContext();

    private final IPreferenceChangeListener prefListener = event -> {
        EclipseUtil.runInUiThread(parent, this::applyConfig);
    };
    
    private final StandingOrdersBuilder standingOrders = new StandingOrdersBuilder()
            .add(aiService)
            .add(workspaceMemoryTool)
            .add(aiService.getAgentsMdService())
            .add(aiService.getSkillService())
            .add(userContext);

    @PostConstruct
    public void createPartControl(Composite parent) {
        this.parent = parent;
        parent.setLayout(new GridLayout(1, false));

        chatHistory = new ChatMarkdownWidget(parent, SWT.BORDER);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // inputBlock carries the single outer border for the entire input area (sections 2+3+4).
        // No background manipulation needed — SWT native widgets render their own correct backgrounds.
        inputBlock = new Composite(parent, SWT.BORDER);
        GridLayout inputBlockLayout = new GridLayout(1, false);
        inputBlockLayout.marginWidth = 0;
        inputBlockLayout.marginHeight = 0;
        inputBlockLayout.verticalSpacing = 0;
        inputBlock.setLayout(inputBlockLayout);
        inputBlock.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        chatInput = new UserInputWidget(inputBlock, SWT.NONE,
            this::doSendMessage,
            () -> getIProgressMonitor().setCanceled(true),
            this::onMicClick);
        chatInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        questionWidget = new UserQuestionWidget(inputBlock, SWT.NONE, this::hideQuestion);
        GridData qgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        qgd.exclude = true;
        questionWidget.setLayoutData(qgd);
        questionWidget.setVisible(false);

        actionsBar = new ActionsBarWidget(inputBlock, SWT.NONE,
            this::onClear,
            this::doStartImpl,
            this::onModeChange,
            aiService::setModel,
            autonomous -> aiService.getAgentMode().setAutonomous(autonomous),
            aiService::withThinking
        );

        statusLine = new StatusLineWidget(inputBlock, SWT.NONE,
            this::onPinChange,
            this::onSkillsToggle,
            enabled -> aiService.getMcpConnectionService().toggle(enabled),
            this::onAgentsMdToggle,
            this::doCompressContext
        );

        statusLine.setSkillsMenuHandler(
            () -> aiService.getSkillService().getAllLoadedSkills(),
            this::onSkillMenuSelection
        );

        applyConfig();

        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);
        updateSelectedProject(EclipseUtil.firstOpenOrSelectedProject());

        aiService.getToolService().addTool(new AskUserTool(
            (question, answers, onAnswer) -> showQuestion(question, answers, onAnswer)
        ));

        aiService.getToolService().addTool(workspaceMemoryTool);
        
        var dateInfo = "Today: " + LocalDate.now() 
                + " — APIs and libraries may have changed since your training cutoff. "
                + "Don't rely only on internal API knowledge — explore base classes and libs if possible with e.g. using "
                + EclipseCodeNavigationTool.GET_TYPE_SOURCE + " for java.";

        aiService.getDeveloperService().setStaticContext(Arrays.asList(SystemMessage.from(dateInfo)));
        aiService.getPlannerService().setStaticContext(Arrays.asList(SystemMessage.from(dateInfo)));

        chatInput.enableSlashCommands(() -> aiService.getCommandService().getCommands());
    }

    private void onClear() {
        var s = aiService.getActiveService();
        s.clear();
        chatHistory.clear();
        statusLine.updateCompact(s.getContextSize(), s.getAutoCompactAfter());
    }

    @PreDestroy
    public void dispose() {
        if (questionWidget != null) questionWidget.cancelSilently();
        InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID).removePreferenceChangeListener(prefListener);
        aiService.disconnectMcp();
        voiceService.close();
    }

    @Focus
    public void setFocus() {
        if (questionWidget != null && questionWidget.isVisible()) questionWidget.setFocus();
        else if (chatInput != null) chatInput.setFocus();
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
        userContext.setTextSelection(ts);
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object o) {
        if (o instanceof ISelection) return;
        userContext.setTextSelection(null);
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
            selection = null;
        } else if (o != null) {
            LOG.info("Unknown resource type selected " + o.getClass());
            selection = null;
        } else {
            selection = null;
        }
        userContext.setSelectedResource(selection);
        updateSelectedProject(EclipseUtil.resolveProject(selection));
    }

    private void updateSelectedProject(IProject project) {
        if (project != null && !userContext.isProjectPinned()) {
            userContext.setCurrentProject(project);
            aiService.setProject(project);
        }
        // TODO add check of project really changed
        if (actionsBar != null) {
            EclipseUtil.runInUiThread(parent, () -> {
                var currentProject = userContext.getCurrentProject();
                actionsBar.setAgentModeAvailable(currentProject != null && currentProject.isOpen());
                if (currentProject == null && aiService.getPeonMode() == PeonMode.AGENT) {
                    onModeChange(PeonMode.DEV);
                }
                refreshStatusLine();
            });
        }
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void setSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object[] selectedObjects) {
        if (selectedObjects != null && selectedObjects.length > 0) {
            setSelection(selectedObjects[0]);
        }
    }

    private void onAgentsMdToggle(boolean enabled) {
        try {
            var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
            prefs.putBoolean(PeonConstants.PREF_AGENTS_MD_ENABLED, enabled);
            prefs.flush();
        } catch (Exception e) {
            LOG.warn("Failed to save agents.md preference", e);
        }
        aiService.getAgentsMdService().setEnabled(enabled);
    }

    // -------------------------------------------------------------------------
    // EclipseAiMonitor
    // -------------------------------------------------------------------------

    @Override
    public void onChatResponse(SimpleMessage m) {
        EclipseUtil.runInUiThread(parent, () -> {
            var ai = aiService.getActiveService();
            chatHistory.hideLiveStatus();
            chatHistory.appendMessage(m);
            statusLine.updateCompact(ai.getContextSize(), ai.getAutoCompactAfter());
        });
    }

    @Override
    public void onCallCompleted(dev.langchain4j.model.chat.response.ChatResponse response, Duration duration) {
        EclipseUtil.runInUiThread(parent, () -> {
            lockWhileWorking(false);
            if (aiService.getAgentMode().consumeImplementationRequest()) {
                aiService.getAgentMode().startImplementation();
            }
            refreshStatusLine();
            actionsBar.updateModeUI(aiService.getPeonMode(), isImplEnabled());
        });
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse r) {
        chatHistory.onStreamingChunk(r);
    }

    @Override
    public void onFileUpdate(AiFileUpdate update) {
        if (parent.isDisposed()) return;
        var diff = SimpleDiff.unifiedDiff(update.file(), update.oldContent(), update.newContent());
        EclipseUtil.runInUiThread(parent, () -> chatHistory.showDiff(diff));
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
            aiService.getSkillService().getSkills().size(),
            aiService.getAgentsMdService().getAgentFileName(),
            aiService.getAgentsMdService().isEnabled(),
            userContext.getCurrentProject(),
            userContext.getSelectedFile()
        );
        var ai = aiService.getActiveService();
        statusLine.updateCompact(ai.getContextSize(), ai.getAutoCompactAfter());
    }

    private void syncAgentsMdToggle() {
        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        boolean enabled = prefs.getBoolean(PeonConstants.PREF_AGENTS_MD_ENABLED, true);
        statusLine.setAgentsMdEnabled(enabled);
        aiService.getAgentsMdService().setEnabled(enabled);
    }

    private void refreshChat() {
        chatHistory.clearMessages();
        aiService.getActiveService().getMessages().forEach(chatHistory::appendMessage);
        refreshStatusLine();
        actionsBar.updateModeUI(aiService.getPeonMode(), isImplEnabled());
    }

    private boolean isImplEnabled() {
        return switch (aiService.getPeonMode()) {
            case PLAN  -> aiService.getPlannerService().getMessages().stream().anyMatch(m -> m.type() == ChatMessageType.AI);
            case AGENT -> aiService.getAgentMode().overviewExists();
            default    -> false;
        };
    }

    // -------------------------------------------------------------------------
    // Config / model loading
    // -------------------------------------------------------------------------

    private void applyConfig() {
        var config = LlmPreferenceInitializer.buildWithDefaults();
        if (lastAppliedConfig != null && lastAppliedConfig.equals(config)) return;
        lastAppliedConfig = config;
        LOG.info("Set new config " + config);
        try {
            aiService.getSkillService().refresh(config.getSkillDirectory());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + config.getSkillDirectory());
        }
        try {
            aiService.getCommandService().refresh(config.getCommandDirectory());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + config.getCommandDirectory());
        }
        aiService.updateConfig(config);
        // Sync the Think toggle to the config default. The user can override this per-session
        // via the button; that override is stored in-memory only and not written to preferences.
        actionsBar.setThinkEnabled(config.isThinkingEnabled());
        applyMcpConfig();
        chatInput.setVoiceInputVisible(VoicePreferenceInitializer.buildWithDefaults().enabled());
        syncAgentsMdToggle();
        refreshStatusLine();
        reloadModelsIfNeeded();
        applyShellCommandConfirmation();
    }

    private void applyMcpConfig() {
        var servers = McpPreferenceInitializer.loadServers();
        statusLine.setMcpAvailable(!servers.isEmpty());
        statusLine.setMcpEnabled(!servers.isEmpty() && McpPreferenceInitializer.isMcpEnabled());
        aiService.applyMcpConfig();
    }

    private void applyShellCommandConfirmation() {
        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        var autonomous = aiService.getAgentMode().getAutonomous();

        // TODO move into own class?
        if ("true".equalsIgnoreCase(prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "")) ||
                "always".equalsIgnoreCase(prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "")) ||
                (!autonomous && "not-autonomous".equalsIgnoreCase(prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "")))) {
            aiService.getToolService().getTool(ShellTool.class).ifPresent(shellTool -> {
                shellTool.setConfirmationProvider((command, workingDirectory) -> {
                    var latch = new java.util.concurrent.CountDownLatch(1);
                    var answer = new AtomicReference<>("No");
                    showQuestion("Allow executing shell command in the \"" + workingDirectory + "\" directory? " +
                            "(or you can enter a new command to execute below)\n\n" + command,
                            List.of("Yes", "No"),
                            a -> { answer.set(a); latch.countDown(); });
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (UserQuestionWidget.CANCEL.equals(answer.get())) {
                        throw new CancellationException("Canceled tool execution " + workingDirectory + " " + command);
                    }
                    return answer.get();
                });
            });

        } else {
            aiService.getToolService().getTool(ShellTool.class).ifPresent(shellTool -> {
                shellTool.setConfirmationProvider(null);
            });
        }
    }

    private void reloadModelsIfNeeded() {
        var config = aiService.getConfig();
        if (StringUtil.hasNoValue(actionsBar.getSelectedModel())
                && StringUtil.hasValue(config.getModel())) {
            actionsBar.setModel(config.getModel());
        }
        if (lastListedConfig.get() == null
                || config.getProviderType() != lastListedConfig.get().getProviderType()
                || !java.util.Objects.equals(config.getUrl(), lastListedConfig.get().getUrl())
                || !java.util.Objects.equals(config.getApiKey(), lastListedConfig.get().getApiKey())) {
            loadModelsInBackground();
        } else {
            EclipseUtil.runInUiThread(parent, () -> {
                if (!actionsBar.containsModelId(config.getModel())) {
                    loadModelsInBackground();
                } else {
                    actionsBar.selectModel(config.getModel());
                }
            });
        }
        lastListedConfig.set(config);
    }

    private void loadModelsInBackground() {
        Job.create("Fetching available models", monitor -> {
            var config = aiService.getConfig();
            try {
                var models = config.listAiModels();
                if (models.isEmpty()) {
                    onChatResponse(new SimpleMessage(Type.PROBLEM, "No models returned by " + config.getUrl()));
                } else {
                    EclipseUtil.runInUiThread(parent, () -> {
                        aiService.resolveModel(models);
                        actionsBar.applyModelList(models, aiService.getConfig().getModel());
                    });
                }
                return Status.OK_STATUS;
            } catch (Exception e) {
                onChatResponse(new SimpleMessage(Type.PROBLEM, e.getMessage()));
                if (StringUtil.hasValue(aiService.getConfig().getModel())) {
                    return new Status(IStatus.WARNING, PeonConstants.PLUGIN_ID, IStatus.OK, 
                            "Failed to load models fallback to " + aiService.getConfig().getModel(), e);
                } else {
                    return new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, IStatus.OK, 
                            "Failed to load models. " + e.getMessage() + " config:\n" + aiService.getConfig(), e);
                }
            }
        }).schedule();
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void onModeChange(PeonMode mode) {
        aiService.getAgentMode().setAutonomous(actionsBar.getAutonomous());
        aiService.setPeonMode(mode);
        refreshChat();
        applyShellCommandConfirmation();
    }

    // TODO 29.03.2026 
    // currentMode should be moved to the aiService
    // so this can all happen in aiService.startImplementation(); returning us the currentMode for the UI
    // maybe we should even name the aiService AIChatViewController
    // refreshChat(); here
    // and sendTrigger.run(); from the AgentModeService can be maybe even be removed? not sure ...
    // at least be moved to the AIChatViewController
    // so doSendMessage(); here can be removed or better be reused? as we use this also as button action
    private void doStartImpl() {
        if (aiService.getPeonMode() == PeonMode.AGENT) {
            aiService.getAgentMode().startImplementation();
            refreshChat();
        } else {
            // PLAN -> DEV: hand off the plan to the developer service
            aiService.setPeonMode(PeonMode.DEV);
            actionsBar.updateModeUI(PeonMode.DEV, true);
            if (aiService.startImplementation()) {
                refreshChat();
                if (StringUtil.hasNoValue(chatInput.getText())) {
                    // some models e.g. Qwen need a use message as last message
                    chatInput.setText("""
                            Start implementing this plan. Save larger plans in the peon-plan/ directory using a sensible filename (for example, based on the title or main goal). 
                            Treat that plan file as your long-term memory when needed. 
                            Keep token usage low: when you switch to a different piece of work, 
                            use the compressor tool to summarize this session and echo the key next steps plus the plan file path in the preserved instructions.
                            """);
                }
                doSendMessage();
            } else {
                onChatResponse(new SimpleMessage(Type.PROBLEM, "Plan missing ..."));
            }
        }
    }

    private void doCompressContext() {
        chatHistory.clear();

        var active = aiService.getActiveService();
        if (active.getMessages().isEmpty()) return;
        lockWhileWorking(true);
        Job.create("Compressing context", monitor -> {
            monitor.beginTask("Compressing chat", IProgressMonitor.UNKNOWN);
            monitorRef.set(monitor);
            Exception ex = null;
            try {
                active.compressContext(this);
                Display.getDefault().asyncExec(this::refreshChat);
            } catch (Exception e) {
                ex = e;
            } finally {
                monitorRef.set(new NullProgressMonitor());
                EclipseUtil.runInUiThread(parent, () -> lockWhileWorking(false));
            }
            monitor.done();
            return PeonConstants.status("Compressed", ex);
        }).schedule();
    }

    private void doSendMessage() {
        if (StringUtil.hasNoValue(aiService.getModel())) {
            chatHistory.appendMessage(new SimpleMessage(Type.PROBLEM, "No model configured — open Window > Preferences > Peon AI"));
            return;
        }

        var active = aiService.getActiveService();
        if (!applySlashCommandIfPresent(active)) return;

        final var selection = userContext.getUserSelection();
        final var needsSelection = !active.hasUserText(selection);

        final var text = StringUtil.strip(chatInput.getText().trim()) + (needsSelection ? selection : "");
        if (StringUtil.hasNoValue(text) && active.getMessages().isEmpty()) return;

        if (StringUtil.hasValue(text)) {
            chatHistory.appendMessage(new SimpleMessage(Type.USER, text));
            chatInput.clearText();
            
            // already working -> we only append the current history ...
            if (actionsBar.isWorking()) {
                active.addMessage(UserMessage.from(text));
                return;
            }
        } else if (actionsBar.isWorking()) { // no text and already working ...
            return;
        }

        lockWhileWorking(true);
        Job.create("Peon AI request", monitor -> {
            monitor.beginTask("Arbeit, Arbeit!", 100);
            monitorRef.set(monitor);
            Exception ex = null;
            ChatResponse cr = null;
            try {
                active.setUserContextInformations(this.standingOrders.build());
                cr = active.call(text.isEmpty() ? null : text, this);
            } catch (ToolExecutionException e) {
                if (!isCanceled()) {
                    if (e.getCause() instanceof CancellationException) {
                        // yes this is fine
                    } else {
                        throw e;
                    }
                }
            } catch (Exception e) {
                if (!isCanceled() || !(e instanceof CancellationException)) {
                    ex = e;
                    LOG.warn("Failed to call LLM " + aiService.getConfig(), e);
                    onChatResponse(new SimpleMessage(Type.PROBLEM, e.getMessage()));
                }
            } finally {
                if (lastAppliedConfig != null && lastAppliedConfig.isDebugMode()) {
                    LOG.info("Chatreponse: " + (cr == null ? "null" : cr.aiMessage()));
                }
                EclipseUtil.runInUiThread(parent, () -> lockWhileWorking(false));
                monitor.done();
                monitorRef.set(new NullProgressMonitor());
            }
            return PeonConstants.status("Peon AI\n" + aiService.getConfig(), ex);
        }).schedule();
    }

    private void onPinChange(boolean pinned) {
        this.userContext.setProjectPinned(pinned);
        if (!pinned && userContext.getSelectedResource() != null) {
            var project = EclipseUtil.resolveProject(userContext.getSelectedResource());
            if (project != null) {
                userContext.setCurrentProject(project);
                aiService.setProject(project);
                actionsBar.setAgentModeAvailable(project.isOpen());
            }
        }
        statusLine.setPinned(pinned);
        refreshStatusLine();
    }

    private void lockWhileWorking(boolean value) {
        actionsBar.lockWhileWorking(value);
        chatInput.setWorking(value);
        if (!value) chatHistory.hideLiveStatus();
        if (!value && questionWidget != null && questionWidget.isVisible()) {
            questionWidget.cancel();
        }
    }

    private void showQuestion(String question, java.util.List<String> answers,
            java.util.function.Consumer<String> onAnswer) {
        chatHistory.updateLiveResponseInUIThread("Wating for User...", 0, null);
        EclipseUtil.runInUiThread(parent, () -> {
            ((GridData) chatInput.getLayoutData()).exclude = true;
            chatInput.setVisible(false);
            ((GridData) questionWidget.getLayoutData()).exclude = false;
            questionWidget.setVisible(true);
            questionWidget.showQuestion(question, answers, a -> {
                chatHistory.appendMessage(new SimpleMessage(Type.USER, a));
                onAnswer.accept(a);
            });
            inputBlock.layout(true, true);
            inputBlock.getParent().layout(new Control[]{ inputBlock });
        });
    }

    private void hideQuestion() {
        ((GridData) chatInput.getLayoutData()).exclude = false;
        chatInput.setVisible(true);
        ((GridData) questionWidget.getLayoutData()).exclude = true;
        questionWidget.setVisible(false);
        questionWidget.hideQuestion();
        inputBlock.layout(true, true);
        inputBlock.getParent().layout(new Control[]{ inputBlock });
    }

    private void onSkillsToggle(boolean enabled) {
        aiService.getSkillService().setEnabled(enabled);
    }

    private void onSkillMenuSelection(SkillMenuSelection selection) {
        if (selection.isAllSkills) {
            aiService.getSkillService().setAllSkillsEnabled(selection.enabled);
        } else {
            aiService.getSkillService().setSkillEnabled(selection.skillName, selection.enabled);
        }
        EclipseUtil.runInUiThread(parent, this::refreshStatusLine);
    }

    /**
     * If the chat input starts with {@code /name}, looks up the command and installs its body as
     * the one-shot system prompt on the active chat service. The slash token is stripped from the
     * input so only the trailing user text is sent. Returns {@code false} and reports a problem
     * when the name is unknown so the caller can abort the send.
     */
    private boolean applySlashCommandIfPresent(AbstractChatService active) {
        var raw = chatInput.getText();
        if (raw == null) return true;
        var trimmed = raw.stripLeading();
        if (!trimmed.startsWith("/")) return true;

        int wsIdx = -1;
        for (int i = 1; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) { wsIdx = i; break; }
        }
        var name = wsIdx < 0 ? trimmed.substring(1) : trimmed.substring(1, wsIdx);
        var rest = wsIdx < 0 ? "" : trimmed.substring(wsIdx).stripLeading();
        if (name.isBlank()) return true;

        var commandService = aiService.getCommandService();
        var command = commandService.get(name);
        if (command.isEmpty()) {
            var available = commandService.commandNames();
            var hint = available.isEmpty() ? "(no commands loaded)" : "Available: " + available;
            chatHistory.appendMessage(new SimpleMessage(Type.PROBLEM,
                    "Unknown command /" + name + ". " + hint));
            return false;
        }

        var prompt = command.get().readBody();
        active.setOneShotSystemPrompt(prompt);
        // If only the slash token was entered, keep it visible as the user turn so the chat
        // history clearly shows which command was invoked AND the LLM receives a non-empty turn.
        chatInput.setText(rest.isEmpty() ? "/" + name : rest);
        chatInput.dismissSlashMenu();
        return true;
    }

    private void onMicClick() {
        if (!recording) {
            recording = true;
            chatInput.setRecording(true);
            try {
                VoiceConfig voice = VoicePreferenceInitializer.buildWithDefaults()
                        .resolve(aiService.getConfig());
                voiceService.startRecording(voice);
            } catch (Exception e) {
                recording = false;
                chatInput.setRecording(false);
                onChatResponse(new SimpleMessage(Type.PROBLEM, "Cannot open microphone: " + e.getMessage()));
            }
        } else {
            recording = false;
            chatInput.setRecording(false);
            Job.create("Transcribing audio", monitor -> {
                try {
                    String text = voiceService.stopAndTranscribe();
                    EclipseUtil.runInUiThread(parent, () -> {
                        chatInput.setText(text);
                        doSendMessage();
                    });
                } catch (Exception e) {
                    return PeonConstants.errorStatus("Transcription failed", e);
                }
                return PeonConstants.okStatus("Transcription finished.");
            }).schedule();
        }
    }
}
