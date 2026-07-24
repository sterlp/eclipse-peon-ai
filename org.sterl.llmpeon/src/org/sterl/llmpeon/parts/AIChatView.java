package org.sterl.llmpeon.parts;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
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
import org.eclipse.jdt.core.IClassFile;
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
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpPreferenceInitializer;
import org.sterl.llmpeon.parts.config.VoicePreferenceInitializer;
import org.sterl.llmpeon.parts.log.EclipseSlf4jLogger;
import org.sterl.llmpeon.parts.model.UserContext;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.parts.widget.ActionsBarWidget;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget;
import org.sterl.llmpeon.parts.widget.HeaderBarWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget.SkillMenuSelection;
import org.sterl.llmpeon.parts.widget.UserInputWidget;
import org.sterl.llmpeon.parts.widget.UserQuestionWidget;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.voice.VoiceConfig;
import org.sterl.llmpeon.voice.VoiceInputService;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
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
        enabled -> EclipseUtil.runInUiThread(parent, () -> statusLine.setMcpEnabled(enabled)),
        () -> EclipseUtil.runInUiThread(parent, this::refreshAgentUI)
    );

    private final AtomicReference<IProgressMonitor> monitorRef = new AtomicReference<>(new NullProgressMonitor());
    private final VoiceInputService voiceService = new VoiceInputService();
    
    private volatile boolean recording = false;

    private HeaderBarWidget headerBar;

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
            .add(WorkspaceMemoryTool.getInstance())
            .add(aiService.getAgentsMdService())
            .add(userContext)
            .add(aiService);

    @PostConstruct
    public void createPartControl(Composite parent) {
        this.parent = parent;
        // No gap between header and chat history — they read as one surface.
        GridLayout rootLayout = new GridLayout(1, false);
        rootLayout.verticalSpacing = 0;
        parent.setLayout(rootLayout);

        headerBar = new HeaderBarWidget(parent, SWT.NONE,
                () -> aiService.getActiveAgent().getName(),
                aiService::getToolStatus);
        headerBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Borderless — a border's top edge would read as a divider against the flush header.
        chatHistory = new ChatMarkdownWidget(parent, SWT.NONE);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // inputBlock carries the single outer border for the entire input area (sections 2+3+4).
        // No background manipulation needed — SWT native widgets render their own correct backgrounds.
        inputBlock = new Composite(parent, SWT.BORDER);
        GridLayout inputBlockLayout = new GridLayout(1, false);
        inputBlockLayout.marginWidth = 0;
        inputBlockLayout.marginHeight = 0;
        inputBlockLayout.verticalSpacing = 0;
        inputBlock.setLayout(inputBlockLayout);
        // Restore the gap above the input block only (root verticalSpacing is 0).
        GridData inputBlockData = new GridData(SWT.FILL, SWT.BOTTOM, true, false);
        inputBlockData.verticalIndent = 5;
        inputBlock.setLayoutData(inputBlockData);

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
            this::onHandoff,
            this::onAgentChange,
            aiService::setModel,
            aiService::withThinking,
            this::doCompressContext
        );
        actionsBar.setModel(aiService.getActiveAgent().getAgentModelName());

        statusLine = new StatusLineWidget(inputBlock, SWT.NONE,
            this::onPinChange,
            this::onSkillsToggle,
            enabled -> aiService.getMcpConnectionService().toggle(enabled),
            this::onAgentsMdToggle
        );

        statusLine.setSkillsMenuHandler(
            () -> aiService.getSkillService().getAllLoadedSkills(),
            this::onSkillMenuSelection
        );
        
        applyConfig();

        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);
        updateSelectedProject(EclipseUtil.firstOpenOrSelectedProject());

        aiService.getSharedToolService().addTool(new AskUserTool(
            (question, answers, onAnswer) -> showQuestion(question, answers, onAnswer)
        ));

        var dateInfo = "Today: " + LocalDate.now() 
                + " — APIs and libraries may have changed since your training cutoff. "
                + "Don't rely only on internal API knowledge — explore base classes and libs if possible with e.g. using "
                + EclipseCodeNavigationTool.GET_TYPE_SOURCE + " for java."
                + "\nos.name: " + System.getProperty("os.name")
                + "\nos file.separator: '" + System.getProperty("file.separator") + "'"
                + "\nos line.separator: '" + System.lineSeparator() + "'"
                + "\nFile access: prefer eclipse* tools over disk* tools. After disk* writes, call eclipseRefreshProject (refresh only) or eclipseBuildProject (refresh + build check) to sync Eclipse."
                + "\nOutside the workspace, use Disk-tools if available this session; if not, ask the user to enable them. Never use shell/terminal for file I/O.";

        aiService.setStaticContext(Arrays.asList(SystemMessage.from(dateInfo)));

        chatInput.enableSlashCommands(() -> {
            var result = new ArrayList<SimplePromptFile>();
            result.addAll(aiService.getCommandService().getCommands());
            result.addAll(aiService.getSkillService().getSkills());
            return result;
        });
    }

    private void onClear() {
        aiService.clear();
        chatHistory.clear();
        actionsBar.updateCompact(0, aiService.getConfig().getAutoCompactAfter());
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
    public void onSelection(@Named(IServiceConstants.ACTIVE_SELECTION) ISelection s) {
        if (s == null || s.isEmpty()) onSelection((Object)null);
        else if (s instanceof IStructuredSelection iss) {
            if (iss.size() == 1) onSelection(iss.getFirstElement());
            else onSelection(iss.toArray());
        }
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void onTextSelection(@Named(IServiceConstants.ACTIVE_SELECTION) ITextSelection ts) {
        EclipseUtil.runInUiThread(parent, () -> {
            userContext.setTextSelection(ts);
            refreshStatusLine();
        });
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void onSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object o) {
        if (o instanceof ITextSelection) return;
        
        userContext.setClassFile(null);
        var selectionElement = EclipseUtil.selectionElement(o).orElse(null);
        if (selectionElement instanceof IClassFile classFile) userContext.setClassFile(classFile);
        var selection = EclipseUtil.resolveResource(selectionElement).orElse(null);
        if (selection == null && selectionElement != null && !(selectionElement instanceof IWorkingSet)) {
            LOG.info("Unknown resource type selected " + selectionElement.getClass());
        }
        userContext.setTextSelection(null);
        userContext.setSelectedResource(selection);
        updateSelectedProject(EclipseUtil.resolveProject(selection));
    }

    private void updateSelectedProject(IProject project) {
        if (project != null && !userContext.isProjectPinned()) {
            aiService.setProject(project);
            var changed = userContext.setCurrentProject(project);
            if (changed) refreshStatusLine();
        }
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void onSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object[] selectedObjects) {
        if (selectedObjects != null && selectedObjects.length > 0) {
            onSelection(selectedObjects[0]);
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
            var ai = aiService.getActiveAgent();
            chatHistory.hideLiveStatus();
            chatHistory.appendMessage(m);
            actionsBar.updateCompact(ai.getMemory().getTotalTokenUsed(), aiService.getConfig().getAutoCompactAfter());
        });
    }

    @Override
    public void onCallCompleted(dev.langchain4j.model.chat.response.ChatResponse response, Duration duration) {
        EclipseUtil.runInUiThread(parent, () -> {
            lockWhileWorking(false);
            refreshStatusLine();
        });
    }

    @Override
    public void onTokenUsage(dev.langchain4j.model.output.TokenUsage usage) {
        EclipseUtil.runInUiThread(parent, () -> headerBar.addTokenUsage(usage));
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

    // TODO: DOUBLE CHECK if refreshStatusLine and refreshChat are 2 methods!
    public void refreshStatusLine() {
        statusLine.update(
            aiService.getSkillService().getSkills().size(),
            aiService.getAgentsMdService().getAgentFileName(),
            aiService.getAgentsMdService().isEnabled(),
            userContext.getCurrentProject(),
            userContext.getSelectedFile()
        );
        var ai = aiService.getActiveAgent();
        actionsBar.updateCompact(ai.getMemory().getTotalTokenUsed(), aiService.getConfig().getAutoCompactAfter());
    }
    private void refreshChat() {
        chatHistory.clear();
        refreshStatusLine();
        aiService.getActiveAgent().getMemory().forEach(chatHistory::appendMessage);
    }

    private void syncAgentsMdToggle() {
        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        boolean enabled = prefs.getBoolean(PeonConstants.PREF_AGENTS_MD_ENABLED, true);
        statusLine.setAgentsMdEnabled(enabled);
        aiService.getAgentsMdService().setEnabled(enabled);
    }


    // -------------------------------------------------------------------------
    // Config / model loading
    // -------------------------------------------------------------------------

    /** Refresh agent combo and status after a config reload. */
    private void refreshAgentUI() {
        actionsBar.setAgents(aiService.getAgents());
        actionsBar.updateModeUI(aiService.getActiveAgent());
        actionsBar.setThinkEnabled(aiService.getActiveAgent().isThinkEnabled());
        refreshStatusLine();
    }

    private void applyConfig() {
        var config = LlmPreferenceInitializer.buildWithDefaults();
        EclipseSlf4jLogger.setDebug(config.isDebugMode());

        if (lastAppliedConfig != null && lastAppliedConfig.equals(config)) return;
        lastAppliedConfig = config;
        LOG.info("Set new config " + config);
        aiService.updateConfig(config);
        EclipseUtil.runInUiThread(parent, () -> {
            actionsBar.setAgents(aiService.getAgents());
            actionsBar.updateModeUI(aiService.getActiveAgent());
        });
        
        // Sync the Think toggle to the selected agent's state (Dev/Plan from prefs, Custom from
        // its AGENT.md). The brain button persists per agent; there is no cascade.
        actionsBar.setThinkEnabled(aiService.getActiveAgent().isThinkEnabled());
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
        var autonomous = false; // TODO restore autonomus mode

        // TODO move into own class?
        if ("true".equalsIgnoreCase(prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "")) ||
                "always".equalsIgnoreCase(prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "")) ||
                (!autonomous && "not-autonomous".equalsIgnoreCase(prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "")))) {
            // TODO is this always needed??!?
            aiService.getSharedToolService().getTool(ShellTool.class).ifPresent(shellTool -> {
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
            aiService.getSharedToolService().getTool(ShellTool.class).ifPresent(shellTool -> {
                shellTool.setConfirmationProvider(null);
            });
        }
    }

    private void reloadModelsIfNeeded() {
        var config = aiService.getConfig();
        var modelName = StringUtil.stripToNull(aiService.getActiveModel());

        if (modelName == null 
                || lastListedConfig.get() == null
                || config.getProviderType() != lastListedConfig.get().getProviderType()
                || !java.util.Objects.equals(config.getUrl(), lastListedConfig.get().getUrl())
                || !java.util.Objects.equals(config.getApiKey(), lastListedConfig.get().getApiKey())) {
            loadModelsInBackground();
        } else {
            EclipseUtil.runInUiThread(parent, () -> {
                if (!actionsBar.containsModelId(modelName)) {
                    actionsBar.setModel(modelName);
                    loadModelsInBackground();
                } else {
                    actionsBar.selectModel(modelName);
                }
            });
        }
        lastListedConfig.set(config);
    }

    private void loadModelsInBackground() {
        Job.create("Fetching available models", monitor -> {
            var config = aiService.getConfig();
            var modelName = StringUtil.stripToNull(aiService.getActiveModel());
            try {
                var models = config.listAiModels();
                if (models.isEmpty()) {
                    onChatResponse(new SimpleMessage(Type.PROBLEM, "No models returned by " + config.getUrl()));
                    showConfiguredModelFallback(modelName); // B1: keep the configured model visible
                } else {
                    EclipseUtil.runInUiThread(parent, () -> {
                        boolean known = modelName != null
                                && models.stream().anyMatch(m -> modelName.equals(m.getId()));
                        if (!known) {
                            // B2: configured model missing (or none) -> adopt the first from the list
                            aiService.setModel(models.getFirst());
                            actionsBar.applyModelList(models, aiService.getActiveModel());
                        } else {
                            actionsBar.applyModelList(models, modelName);
                        }
                    });
                }
                return Status.OK_STATUS;
            } catch (Exception e) {
                onChatResponse(new SimpleMessage(Type.PROBLEM, config.getProviderType().name() + ": " + e.getMessage()));
                showConfiguredModelFallback(modelName); // B1: keep the configured model visible
                if (StringUtil.hasValue(modelName)) {
                    return new Status(IStatus.WARNING, PeonConstants.PLUGIN_ID, IStatus.OK, 
                            "Failed to load models fallback to " + modelName, e);
                } else {
                    return new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, IStatus.OK, 
                            "Failed to load models. " + e.getMessage() + " config:\n" + aiService.getConfig(), e);
                }
            }
        }).schedule();
    }

    /**
     * B1: when the model list is empty or failed to load, still show the model configured for the
     * active agent instead of leaving the dropdown empty.
     */
    private void showConfiguredModelFallback(String modelName) {
        if (StringUtil.hasValue(modelName)) {
            EclipseUtil.runInUiThread(parent, () -> actionsBar.setModel(modelName));
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void onAgentChange(AiAgent mode) {
        aiService.setActiveAgent(mode);

        if (!actionsBar.containsModelId(aiService.getActiveModel())) {
            actionsBar.addAndSelectModel(aiService.getActiveModel());
        } else {
            actionsBar.selectModel(aiService.getActiveModel());
        }

        // brain toggle follows the newly selected agent (no cascade)
        actionsBar.setThinkEnabled(aiService.getActiveAgent().isThinkEnabled());

        // Show scaffold tutorial on first activation
        var tutorial = aiService.getScaffoldTutorial();
        if (tutorial != null) {
            onChatResponse(new SimpleMessage(Type.AI, tutorial));
        }

        refreshChat();
    }

    private void onHandoff() {
        if (aiService.onHandoff()) {
            AiAgent agent = aiService.getActiveAgent();
            actionsBar.updateModeUI(agent);
            this.refreshChat();
            this.refreshStatusLine();
            
            if (StringUtil.hasNoValue(chatInput.getText()) && !aiService.hasPlan()) {
                // some models e.g. Qwen need a use message as last message
                // compactSession
                chatInput.setText("""
                    Implement the plan.
                    
                    If the plan is large, save it using a filename derived from the feature name (if not already done).
                    Treat the plan file as long-term memory — update it as decisions are made or steps completed.
                    Create separete task file for each individual feature you implement and work on them individually.
                    
                    When switching to a different piece of work:
                    1. Batch in parallel: run compactSession on the current conversation + read the plan file + read any referenced files or prior plans.
                    2. Pass into the preserve parameter: this handover instruction, the plan file path, and the next steps.
                    """);
            }
            doSendMessage();

        } else {
            onChatResponse(new SimpleMessage(Type.PROBLEM, "Plan or Agent '" + aiService.getActiveAgent().handoverTo() + "' missing ..."));
        }
    }

    private void doCompressContext() {
        var active = aiService.getActiveAgent();
        if (active.getMemory().size() == 0) return;
        lockWhileWorking(true);
        Job.create("Compressing context", monitor -> {
            monitor.beginTask("Compressing chat", 1);
            monitorRef.set(monitor);
            Exception ex = null;
            ChatResponse cr = null;
            try {
                cr = active.compressContext(this);
                Display.getDefault().asyncExec(this::refreshChat);
            } catch (Exception e) {
                ex = handleChatException(e);
            } finally {
                handleDoneChatResponse(cr, monitor);
            }
            return PeonConstants.status("Compressed", ex);
        }).schedule();
    }

    private void doSendMessage() {
        if (StringUtil.hasNoValue(aiService.getActiveModel())) {
            chatHistory.appendMessage(new SimpleMessage(Type.PROBLEM, "No model configured — open Window > Preferences > Peon AI"));
            return;
        }

        var active = aiService.getActiveAgent();

        final var text = StringUtil.strip(chatInput.getText().trim());
        if (StringUtil.hasNoValue(text) && active.getMemory().size() == 0) return;

        if (StringUtil.hasValue(text)) {
            applySlashCommandIfPresent();
            chatHistory.appendMessage(new SimpleMessage(Type.USER, text));
            chatInput.clearText();
            
            // already working -> we only append the current history ...
            if (actionsBar.isWorking()) {
                active.getMemory().add(UserMessage.from(text));
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
            } catch (Exception e) {
                ex = handleChatException(e);
            } finally {
                handleDoneChatResponse(cr, monitor);
            }
            return PeonConstants.status("Peon AI\n" + aiService.getConfig(), ex);
        }).schedule();
    }

    private void handleDoneChatResponse(ChatResponse cr,
            IProgressMonitor monitor) {
        if (aiService.getConfig().isDebugMode()) {
            LOG.info("Chatreponse: " + (cr == null ? "null" : cr.aiMessage()));
        }
        monitor.done();
        monitorRef.set(new NullProgressMonitor());
        EclipseUtil.runInUiThread(parent, () -> lockWhileWorking(false));
    }
    
    private Exception handleChatException(Exception e) {
        if (e == null) return null;
        if (isCanceled()) return null;
        if (e instanceof CancellationException) return null;
        var cause = e.getCause();
        if (cause instanceof CancellationException) return null;
        
        if (e instanceof RateLimitException || cause instanceof RateLimitException) {
            onChatResponse(new SimpleMessage(Type.PROBLEM, "API rate limit! " + e.getMessage()));
            return null;
        }
        LOG.warn("Failed to call LLM " + aiService.getConfig(), e);
        if (aiService.getConfig().isDebugMode()) {
            aiService.getActiveAgent().getMemory().printMessages();
        }
        onChatResponse(new SimpleMessage(Type.PROBLEM, e.getMessage()));
        return e;
    }

    private void onPinChange(boolean pinned) {
        this.userContext.setProjectPinned(pinned);
        if (!pinned && userContext.getSelectedResource() != null) {
            var project = EclipseUtil.resolveProject(userContext.getSelectedResource());
            if (project != null) {
                userContext.setCurrentProject(project);
                aiService.setProject(project);
            }
        }
        statusLine.setPinned(pinned);
        refreshStatusLine();
    }

    private void lockWhileWorking(boolean value) {
        if (parent == null || parent.isDisposed()) return;
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
     * If the chat input starts with {@code /name}, looks up the command (or skill) and adds its body
     * as a one-time standing order, so it is prepended to the message and re-injected if the session
     * is compacted mid-task. The slash token is stripped so only the trailing user text is sent.
     * Reports a problem when the name is unknown so the caller can abort the send.
     */
    private void applySlashCommandIfPresent() {
        var raw = chatInput.getText();
        if (raw == null) return;
        var trimmed = raw.stripLeading();
        if (!trimmed.startsWith("/")) return;

        int wsIdx = -1;
        for (int i = 1; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) { wsIdx = i; break; }
        }
        var name = wsIdx < 0 ? trimmed.substring(1) : trimmed.substring(1, wsIdx);
        if (name.isBlank()) return;

        var commandService = aiService.getCommandService();
        var command = commandService.get(name);
        if (command.isPresent()) {
            standingOrders.addOneTimeOrder(command.get().getBody());
        } else {
            var skillService = aiService.getSkillService();
            var skill = skillService.get(name);
            if (skill.isPresent()) {
                standingOrders.addOneTimeOrder(skill.get().getBody() + "\n\nExecute this skill on the following instruction - full body was loaded.");
            } else {
                if (!commandService.hasCommands() && !skillService.hasSkills()) return;
                var available = commandService.commandNames() + ", " + skillService.skillNames();
                chatHistory.appendMessage(new SimpleMessage(Type.PROBLEM,
                        "Unknown command or SKILL /" + name + ". Available " + available));
                
                return;
            }
        }
        chatInput.dismissSlashMenu();
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
