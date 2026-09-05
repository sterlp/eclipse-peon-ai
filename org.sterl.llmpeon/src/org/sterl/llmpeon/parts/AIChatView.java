package org.sterl.llmpeon.parts;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
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
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.SlashCommandResolver;
import org.sterl.llmpeon.command.SlashCommandResolver.SlashResult;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.exception.ExceptionUtil;
import org.sterl.llmpeon.parts.ai.PeonAiService;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpPreferenceInitializer;
import org.sterl.llmpeon.parts.config.VoicePreferenceInitializer;
import org.sterl.llmpeon.parts.log.EclipseSlf4jLogger;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.parts.widget.ActionsBarWidget;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget;
import org.sterl.llmpeon.parts.widget.HeaderBarWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget.SkillMenuSelection;
import org.sterl.llmpeon.parts.widget.UserInputWidget;
import org.sterl.llmpeon.parts.widget.UserQuestionResponseWidget;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.voice.VoiceConfig;
import org.sterl.llmpeon.voice.VoiceInputService;

import dev.langchain4j.model.chat.request.ChatRequest;
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

    private volatile LlmConfig lastAppliedConfig = null;

    private ChatMarkdownWidget chatHistory;
    private Composite inputBlock;
    private UserInputWidget chatInput;
    private UserQuestionResponseWidget questionWidget;

    private final IPreferenceChangeListener prefListener = event -> {
        EclipseUtil.runInUiThread(parent, this::applyConfig);
    };

    @PostConstruct
    public void createPartControl(Composite parent) {
        this.parent = parent;
        // No gap between header and chat history — they read as one surface.
        GridLayout rootLayout = new GridLayout(1, false);
        rootLayout.verticalSpacing = 0;
        parent.setLayout(rootLayout);

        headerBar = new HeaderBarWidget(parent, SWT.NONE,
                () -> aiService.getActiveAgent().getName(),
                aiService::getToolStatus,
                aiService::getStatusAgents);
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

        questionWidget = new UserQuestionResponseWidget(inputBlock, SWT.NONE, this::hideQuestion);
        GridData qgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        qgd.exclude = true;
        questionWidget.setLayoutData(qgd);
        questionWidget.setVisible(false);

        actionsBar = new ActionsBarWidget(inputBlock, SWT.NONE,
            this::onClear,
            this::onHandoff,
            this::onAgentChange,
            this::doCompressContext
        );

        statusLine = new StatusLineWidget(inputBlock, SWT.NONE,
            this::onPinChange,
            this::onSkillsToggle,
            enabled -> aiService.getMcpConnectionService().toggle(enabled)
        );

        statusLine.setSkillsMenuHandler(
            () -> aiService.getSkillService().getAllLoadedSkills(),
            this::onSkillMenuSelection
        );

        applyConfig();
        refreshChat();

        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);
        updateSelectedProject(EclipseUtil.firstOpenOrSelectedProject());

        aiService.getSharedToolService().addTool(new AskUserTool(
            (question, answers, onAnswer) -> showQuestion(question, answers, onAnswer)
        ));

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
        if (parent == null || parent.isDisposed()) return;
        aiService.getUserContext().setTextSelection(ts);
        EclipseUtil.runInUiThread(parent, this::refreshStatusLine);
    }

    @Inject
    @org.eclipse.e4.core.di.annotations.Optional
    public void onSelection(@Named(IServiceConstants.ACTIVE_SELECTION) Object o) {
        if (o instanceof ITextSelection) return;
        if (parent == null || parent.isDisposed()) return;

        aiService.getUserContext().setClassFile(null);
        var selectionElement = EclipseUtil.selectionElement(o).orElse(null);
        if (selectionElement instanceof IClassFile classFile) aiService.getUserContext().setClassFile(classFile);
        var selection = EclipseUtil.resolveResource(selectionElement).orElse(null);
        if (selection == null && selectionElement != null && !(selectionElement instanceof IWorkingSet)
                && !selectionElement.getClass().getName().equals("org.eclipse.ui.internal.views.log.LogEntry")
                && aiService.getConfig().isDebugMode()) {
            LOG.info("Unknown resource type selected " + selectionElement.getClass());
        }
        aiService.getUserContext().setTextSelection(null);
        aiService.getUserContext().setSelectedResource(selection);
        updateSelectedProject(EclipseUtil.resolveProject(selection));
    }

    private void updateSelectedProject(IProject project) {
        if (project != null && !aiService.getUserContext().isProjectPinned()) {
            var changed = aiService.setProject(project);
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

    // -------------------------------------------------------------------------
    // EclipseAiMonitor
    // -------------------------------------------------------------------------

    @Override
    public void onChatMessage(int iteration, ChatRequest.Builder request) {
        chatHistory.updateLiveResponseInUIThread("waiting for AI...", 0, null);
        // First loop callback where the agent's working flag is already true — light up the 🟢.
        EclipseUtil.runInUiThread(parent, () -> headerBar.refreshRoster());
    }

    @Override
    public void onChatResponse(SimpleMessage m) {
        EclipseUtil.runInUiThread(parent, () -> {
            var ai = aiService.getActiveAgent();
            if (m.role() == Type.TOOL) {
                chatHistory.updateLiveResponseInUIThread(m.message(), 0, "");
            } else {
                chatHistory.hideLiveStatus();
            }

            chatHistory.appendMessage(m);
            actionsBar.updateCompact(ai.getMemory().getTotalTokenUsed(), aiService.getConfig().getAutoCompactAfter());
            headerBar.refreshRoster();
        });
    }

    @Override
    public void onCallCompleted(dev.langchain4j.model.chat.response.ChatResponse response, Duration duration) {
        EclipseUtil.runInUiThread(parent, this::refreshStatusLine);
    }

    @Override
    public void onTokenUsage(dev.langchain4j.model.output.TokenUsage usage) {
        EclipseUtil.runInUiThread(parent, () -> {
            headerBar.addTokenUsage(usage);
            headerBar.refreshRoster();
        });
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse r) {
        if (parent.isDisposed()) return;
        EclipseUtil.runInUiThread(parent, () -> chatHistory.onStreamingChunk(r));
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
        if (statusLine == null) return;
        if (actionsBar == null) return;

        statusLine.update(
            aiService.getSkillService().getSkills().size(),
            aiService.getProject(),
            aiService.getUserContext().getSelectedFile()
        );

        var ai = aiService.getActiveAgent();
        actionsBar.updateCompact(ai.getMemory().getTotalTokenUsed(), aiService.getConfig().getAutoCompactAfter());
    }

    private void refreshChat() {
        chatHistory.clear();
        refreshStatusLine();
        aiService.getActiveAgent().getMemory().forEach(chatHistory::appendMessage);
    }

    // -------------------------------------------------------------------------
    // Config / model loading
    // -------------------------------------------------------------------------

    /** Refresh agent combo and status after a config reload. */
    private void refreshAgentUI() {
        actionsBar.setAgents(aiService.getAgents());
        actionsBar.updateModeUI(aiService.getActiveAgent());
        refreshChat();
    }

    private void applyConfig() {
        var config = LlmPreferenceInitializer.buildWithDefaults();
        EclipseSlf4jLogger.setDebug(config.isDebugMode());
        LOG.info("Set new config " + config);
        // ensure we set the voice config as we break later ...
        chatInput.setVoiceInputVisible(VoicePreferenceInitializer.buildWithDefaults().enabled());
        chatHistory.setShowRealtimeAiResponse(config.isShowRealtimeAiResponse());

        if (lastAppliedConfig != null && lastAppliedConfig.equals(config)) return;
        lastAppliedConfig = config;
        aiService.updateConfig(config);

        actionsBar.setAgents(aiService.getAgents());
        actionsBar.updateModeUI(aiService.getActiveAgent());
        applyMcpConfig();
        refreshStatusLine();
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
        var autonomous = this.aiService.getActiveAgent() instanceof AiPlanAgent;

        // TODO move into own class?
        var shellSetting = prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "");
        if ("true".equalsIgnoreCase(shellSetting) ||
                "always".equalsIgnoreCase(shellSetting) ||
                (!autonomous && "not-autonomous".equalsIgnoreCase(shellSetting))) {
            // TODO is this always needed??!?
            aiService.getSharedToolService().getTool(ShellTool.class).ifPresent(shellTool -> {
                shellTool.setConfirmationProvider((command, workingDirectory) -> {
                    var latch = new java.util.concurrent.CountDownLatch(1);
                    var answer = new AtomicReference<>("No");
                    showQuestion("Approve execution of:"
                            + "\n\n`" + command + "`"
                            + "\n in **" + workingDirectory + "**? \n\n"
                            + "or enter a new command to execute:",
                            List.of("Yes", "No"),
                            a -> { answer.set(a); latch.countDown(); });
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (UserQuestionResponseWidget.CANCEL.equals(answer.get())) {
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

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void onAgentChange(AiAgent mode) {
        aiService.setActiveAgent(mode);

        // The header status widget pulls the new agent's team live on the next refresh — nothing to
        // reset here (the old onSubAgent chip state is gone; the widget holds no per-agent state).
        headerBar.refreshRoster();

        // Show scaffold tutorial on first activation
        var tutorial = aiService.getScaffoldTutorial();
        if (tutorial != null) {
            onChatResponse(new SimpleMessage(Type.AI, tutorial));
        }

        // Jon on an empty, undocumented workspace: greet + explain how he works (no docs/index.md yet)
        var poTutorial = aiService.getPoTutorial();
        if (poTutorial != null) {
            onChatResponse(new SimpleMessage(Type.AI, poTutorial));
        }

        refreshChat();
    }

    private void onHandoff() {
        if (aiService.onHandoff()) {
            AiAgent agent = aiService.getActiveAgent();
            actionsBar.updateModeUI(agent);
            this.refreshChat();
            this.refreshStatusLine();
            doSendMessage();
        } else {
            onChatResponse(new SimpleMessage(Type.PROBLEM, "Plan or Agent '" + aiService.getActiveAgent().handoverTo() + "' missing ..."));
        }
    }

    private void doCompressContext() {
        var active = aiService.getActiveAgent();
        if (active.getMemory().size() < 3) return;
        lockWhileWorking(true);
        chatHistory.clear();
        Job.create("Compressing context", monitor -> {
            monitor.beginTask("Compressing chat", 1);
            monitorRef.set(monitor);
            Exception ex = null;
            ChatResponse cr = null;
            try {
                cr = active.compact(this);
            } catch (Exception e) {
                ex = handleChatException(e);
            } finally {
                // own refresh to ensure the onTool messages are preserved after compact
                Display.getDefault().asyncExec(() -> {
                    refreshStatusLine();
                    aiService.getActiveAgent().getMemory().forEach(chatHistory::appendMessage);
                    chatHistory.hideLiveStatus();
                });
                handleDoneChatResponse(cr, monitor, ex);
            }
            return PeonConstants.status("Compressed", ex);
        }).schedule();
    }

    private sealed interface SendDecision {
        record Skip() implements SendDecision {}
        record Submit(String messageOrNull) implements SendDecision {}
    }

    private void doSendMessage() {
        if (StringUtil.hasNoValue(aiService.getActiveModel())) {
            chatHistory.appendMessage(new SimpleMessage(Type.PROBLEM, "No model configured — open Window > Preferences > Peon AI"));
            return;
        }

        var active = aiService.getActiveAgent();
        final var text = StringUtil.strip(chatInput.getText().trim());
        if (StringUtil.hasNoValue(text) && active.getMemory().size() == 0) return;

        String messageToSend = null;

        if (StringUtil.hasValue(text)) {
            var decision = resolveOutgoingMessage(text, active);
            if (decision instanceof SendDecision.Skip) return;
            messageToSend = ((SendDecision.Submit) decision).messageOrNull();
        } else if (active.isWorking()) {
            return;
        }

        submitAiJob(messageToSend);
    }

    private SendDecision resolveOutgoingMessage(String text, AiAgent active) {
        var resolver = new SlashCommandResolver();
        var result = resolver.resolve(text, aiService.getCommandService(), aiService.getSkillService());

        String trailing;
        if (result.isPresent()) {
            SlashResult r = result.get();
            var label = r.isSkill() ? "Using 🧩: " + r.name() : "Using 🪄: " + r.name();
            this.aiService.getUserContext().addOneTimeOrder(
                    new SimpleContextItem(label, r.body())
            );
            chatHistory.appendMessage(new SimpleMessage(Type.TOOL, label));
            trailing = StringUtil.hasValue(r.trailingText()) ? r.trailingText() : null;
            if (trailing != null) {
                chatHistory.appendMessage(new SimpleMessage(Type.USER, trailing));
            }
            chatInput.dismissSlashMenu();
        } else {
            trailing = text;
            chatHistory.appendMessage(new SimpleMessage(Type.USER, text));
        }
        chatInput.clearText();

        // TODO can we move this to the chat service?
        if (active.isWorking()) {
            if (trailing != null) {
                boolean isNewEntry = active.queueMessage(trailing); // delegates to agent's queue
                if (isNewEntry) {
                    chatHistory.appendMessage(new SimpleMessage(Type.AI,
                            "Noted, I will respond as soon as I finished..."));
                }
                chatHistory.updateLiveResponseInUIThread("Noted user message ...", 0, "");
            }
            return new SendDecision.Skip();
        }
        return new SendDecision.Submit(trailing);
    }

    private void submitAiJob(String messageToSend) {
        lockWhileWorking(true);
        Job.create("Peon AI request", monitor -> {
            monitor.beginTask("Arbeit, Arbeit!", 100);
            monitorRef.set(monitor);
            Exception ex = null;
            ChatResponse cr = null;
            try {
                cr = aiService.call(messageToSend, this);
            } catch (Exception e) {
                ex = handleChatException(e);
            } finally {
                handleDoneChatResponse(cr, monitor, ex);
            }
            return PeonConstants.status("Peon AI\n" + aiService.getConfig(), ex);
        }).schedule();
    }

    private void handleDoneChatResponse(ChatResponse cr, IProgressMonitor monitor, Exception ex) {
        if (aiService.getConfig().isDebugMode()) {
            LOG.info("Chatreponse: " + (cr == null ? "null" : cr.aiMessage()));
        }
        monitor.done();
        monitorRef.set(new NullProgressMonitor());
        EclipseUtil.runInUiThread(parent, () -> {
            // Queue drain on abort is handled in core by AbstractAgent.handleAbortAndDrain() — ADR-0017
            lockWhileWorking(false);
            actionsBar.updateCompact(
                    aiService.getActiveAgent().getMemory().getTotalTokenUsed(),
                    aiService.getConfig().getAutoCompactAfter());
            chatHistory.hideLiveStatus();
        });
    }

    private Exception handleChatException(Exception e) {
        if (e == null) return null;
        if (isCanceled()) return null;
        if (ExceptionUtil.isCanceled(e)) return null;
        if (ExceptionUtil.isRateLimit(e)) {
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
        aiService.getUserContext().setProjectPinned(pinned);
        if (!pinned && aiService.getUserContext().getSelectedResource() != null) {
            var project = EclipseUtil.resolveProject(aiService.getUserContext().getSelectedResource());
            if (project != null)  aiService.setProject(project);
        }
        statusLine.setPinned(pinned);
        refreshStatusLine();
    }

    private void lockWhileWorking(boolean value) {
        if (parent == null || parent.isDisposed()) return;
        actionsBar.lockWhileWorking(value);
        chatInput.isWorking(value);
        headerBar.refreshRoster(); // reflect work start/end (esp. clear the 🟢 on end)
        if (!value) chatHistory.hideLiveStatus();
        if (!value && questionWidget != null && questionWidget.isVisible()) {
            questionWidget.cancel();
        }
    }

    private void showQuestion(String question, List<String> answers,
            java.util.function.Consumer<String> onAnswer) {
        EclipseUtil.runInUiThread(parent, () -> {
            chatHistory.hideLiveStatus();
            ((GridData) chatInput.getLayoutData()).exclude = true;
            chatInput.setVisible(false);
            ((GridData) questionWidget.getLayoutData()).exclude = false;
            questionWidget.setVisible(true);
            chatHistory.appendMessage(new SimpleMessage(Type.QUESTION, question));
            questionWidget.showQuestion(answers, a -> {
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
        // Restore live status — work is still in progress while user answered.
        chatHistory.updateLiveResponseInUIThread("waiting for AI...", 0, null);
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
