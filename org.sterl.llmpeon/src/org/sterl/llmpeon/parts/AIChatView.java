package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
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
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkingSet;
import org.sterl.llmpeon.AbstractChatService;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpPreferenceInitializer;
import org.sterl.llmpeon.parts.config.VoicePreferenceInitializer;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.widget.ActionsBarWidget;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget;
import org.sterl.llmpeon.parts.widget.UserInputWidget;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;
import org.sterl.llmpeon.voice.VoiceConfig;
import org.sterl.llmpeon.voice.VoiceInputService;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
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
    private boolean recording = false;

    private PeonMode currentMode = PeonMode.DEV;
    private LlmConfig lastListedConfig;
    private IProject currentProject;
    private boolean projectPinned = false;

    private ChatMarkdownWidget chatHistory;
    private UserInputWidget chatInput;

    private ITextSelection textSelection;
    private IResource selectedResource;

    private final IPreferenceChangeListener prefListener = event -> {
        EclipseUtil.runInUiThread(parent, this::applyConfig);
    };

    @PostConstruct
    public void createPartControl(Composite parent) {
        this.parent = parent;
        parent.setLayout(new GridLayout(1, false));

        chatHistory = new ChatMarkdownWidget(parent, SWT.BORDER);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // inputBlock carries the single outer border for the entire input area (sections 2+3+4).
        // No background manipulation needed — SWT native widgets render their own correct backgrounds.
        Composite inputBlock = new Composite(parent, SWT.BORDER);
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

        actionsBar = new ActionsBarWidget(inputBlock, SWT.NONE,
            this::onClear,
            this::doStartImpl,
            this::onModeChange,
            model -> aiService.updateConfig(aiService.getDeveloperService().getConfig().withModel(model)),
            autonomous -> aiService.getAgentMode().setAutonomous(autonomous),
            this::onThinkToggle
        );

        statusLine = new StatusLineWidget(inputBlock, SWT.NONE,
            this::onPinChange,
            this::onSkillsToggle,
            enabled -> aiService.getMcpConnectionService().toggle(enabled),
            this::doCompressContext
        );

        applyConfig();

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);

        updateSelectedProject(EclipseUtil.firstOpenOrSelectedProject());
    }

    private void onClear() {
        getActiveService().clear();
        chatHistory.clear();
        statusLine.updateCompact(getActiveService().getTokenSize(), getActiveService().getTokenWindow());
    }

    @PreDestroy
    public void dispose() {
        InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID).removePreferenceChangeListener(prefListener);
        aiService.disconnectMcp();
        voiceService.close();
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
            selection = selectedResource;
        } else if (o != null) {
            LOG.info("!!! Unknown resource type selected " + o.getClass());
            selection = selectedResource;
        } else {
            selection = null;
        }
        selectedResource = selection;
        if (selectedResource != null) {
            LOG.info("Selected " + selectedResource.getName());
        }

        var project = EclipseUtil.resolveProject(selection);
        updateSelectedProject(project);
    }

    private void updateSelectedProject(IProject project) {
        if (project != null && !projectPinned) {
            currentProject = project;
            aiService.setProject(project);
        }

        if (actionsBar != null) {
            EclipseUtil.runInUiThread(parent, () -> {
                actionsBar.setAgentModeAvailable(currentProject != null && currentProject.isOpen());
                if (currentProject == null && currentMode == PeonMode.AGENT) {
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

    // -------------------------------------------------------------------------
    // EclipseAiMonitor
    // -------------------------------------------------------------------------

    @Override
    public void onChatResponse(SimpleMessage m) {
        EclipseUtil.runInUiThread(parent, () -> {
            chatHistory.appendMessage(m);
            statusLine.updateCompact(getActiveService().getTokenSize(), getActiveService().getTokenWindow());
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
            actionsBar.updateModeUI(currentMode, isImplEnabled());
        });
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
            aiService.getSkillService().loadedSkillCount(),
            aiService.getAgentsMdService().hasAgentFile(),
            currentProject,
            getSelectedFile()
        );
        var active = getActiveService();
        statusLine.updateCompact(active.getTokenSize(), active.getTokenWindow());
    }

    private void refreshChat() {
        chatHistory.clearMessages();
        getActiveService().getMessages().forEach(chatHistory::appendMessage);
        refreshStatusLine();
        actionsBar.updateModeUI(currentMode, isImplEnabled());
    }

    private boolean isImplEnabled() {
        return switch (currentMode) {
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
        try {
            aiService.getSkillService().refresh(config.getSkillDirectory());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + config.getSkillDirectory());
        }
        aiService.updateConfig(config);
        // Sync the Think toggle to the config default. The user can override this per-session
        // via the button; that override is stored in-memory only and not written to preferences.
        actionsBar.setThinkEnabled(config.isThinkingEnabled());
        applyMcpConfig();
        chatInput.setVoiceInputVisible(VoicePreferenceInitializer.buildWithDefaults().enabled());
        refreshStatusLine();
        onConfigChanged(config);
    }

    private void applyMcpConfig() {
        var servers = McpPreferenceInitializer.loadServers();
        statusLine.setMcpAvailable(!servers.isEmpty());
        statusLine.setMcpEnabled(!servers.isEmpty() && McpPreferenceInitializer.isMcpEnabled());
        aiService.applyMcpConfig();
    }

    private void onConfigChanged(LlmConfig config) {
        if (lastListedConfig == null
                || config.getProviderType() != lastListedConfig.getProviderType()
                || !java.util.Objects.equals(config.getUrl(), lastListedConfig.getUrl())
                || !java.util.Objects.equals(config.getApiKey(), lastListedConfig.getApiKey())) {
            loadModelsInBackground(config);
        } else {
            EclipseUtil.runInUiThread(parent, () -> {
                if (parent.isDisposed()) return;
                if (!actionsBar.containsModelId(config.getModel())) {
                    loadModelsInBackground(config);
                } else {
                    actionsBar.selectModel(config.getModel());
                }
            });
        }
    }

    private void loadModelsInBackground(LlmConfig config) {
        Job.create("Fetching available models", monitor -> {
            List<AiModel> models = config.getProviderType().listAiModels(config);
            lastListedConfig = config;
            EclipseUtil.runInUiThread(parent, () -> {
                actionsBar.applyModelList(models, config.getModel());
                var resolved = config.resolveModel(models);
                if (!resolved.equals(config)) aiService.updateConfig(resolved);
            });
            return Status.OK_STATUS;
        }).schedule();
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void onModeChange(PeonMode mode) {
        if (mode == PeonMode.AGENT) {
            aiService.getToolService().addTool(aiService.getAgentModeTools());
            aiService.getAgentMode().reset();
            aiService.getAgentMode().setAutonomous(true);
            actionsBar.setAutonomous(true);
            if (aiService.getAgentMode().overviewExists()) {
                aiService.getAgentMode().getActiveService().addMessage(UserMessage.from(
                        "Existing plan found:\n\n" + aiService.getAgentMode().readOverview()));
                aiService.getAgentMode().openOverviewInEditor();
            }
        } else {
            aiService.getToolService().removeTool(aiService.getAgentModeTools());
            aiService.getAgentMode().reset();
        }
        currentMode = mode;
        refreshChat();
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
        if (currentMode == PeonMode.AGENT) {
            aiService.getAgentMode().startImplementation();
            refreshChat();
            return;
        }

        // PLAN -> DEV: hand off the plan to the developer service
        currentMode = PeonMode.DEV;
        actionsBar.updateModeUI(PeonMode.DEV, true);
        aiService.startImplementation();
        refreshChat();
        doSendMessage();
    }

    private void doCompressContext() {
        chatHistory.clear();

        var active = getActiveService();
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
        if (StringUtil.hasNoValue(aiService.getDeveloperService().getConfig().getModel())) {
            chatHistory.appendMessage(new SimpleMessage(Type.PROBLEM, "No model configured — open Window > Preferences > Peon AI"));
            return;
        }

        final var active = getActiveService();
        final var selection = getUserSelection();
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
            monitor.beginTask("Arbeit, Arbeit!", currentMode == PeonMode.AGENT ? ToolService.MAX_ITERATIONS * 2 : ToolService.MAX_ITERATIONS);
            monitorRef.set(monitor);
            Exception ex = null;
            try {
                active.setStandingOrders(StandingOrdersBuilder.build(
                        selectedResource, aiService.getAgentsMdService(), aiService.getTemplateContext(),
                        currentMode, aiService.getAgentMode()));
                
                active.call(text.isEmpty() ? null : text, this);

            } catch (Exception e) {
                ex = e;
                onChatResponse(new SimpleMessage(Type.PROBLEM, e.getMessage()));
            } finally {
                EclipseUtil.runInUiThread(parent, () -> lockWhileWorking(false));
                monitor.done();
                active.setStandingOrders(Collections.emptyList());
                monitorRef.set(new NullProgressMonitor());
            }
            return PeonConstants.status("Peon AI\n" + aiService.getDeveloperService().getConfig(), ex);
        }).schedule();
    }

    private void onPinChange(boolean pinned) {
        this.projectPinned = pinned;
        if (!pinned && selectedResource != null) {
            var project = EclipseUtil.resolveProject(selectedResource);
            if (project != null) {
                currentProject = project;
                aiService.setProject(project);
            }
            actionsBar.setAgentModeAvailable(currentProject != null && currentProject.isOpen());
        }
        EclipseUtil.runInUiThread(parent, () -> {
            statusLine.setPinned(pinned);
            refreshStatusLine();
        });
    }

    private void lockWhileWorking(boolean value) {
        actionsBar.lockWhileWorking(value);
        chatInput.setWorking(value);
    }

    private void onThinkToggle(boolean enabled) {
        aiService.updateConfig(aiService.getDeveloperService().getConfig().withThinking(enabled));
    }

    private void onSkillsToggle(boolean enabled) {
        aiService.getSkillService().setEnabled(enabled);
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

    private AbstractChatService getActiveService() {
        return switch (currentMode) {
            case DEV   -> aiService.getDeveloperService();
            case PLAN  -> aiService.getPlannerService();
            case AGENT -> aiService.getAgentMode().getActiveService();
        };
    }

    private String getUserSelection() {
        if (textSelection == null || StringUtil.hasNoValue(textSelection.getText())) return "";
        var file = getSelectedFile();

        var extension = "\n";
        if (file != null) extension = file.getFileExtension() + "\n";

        String userIn = "\n\n```" + extension + textSelection.getText() + "\n```";

        if (file != null) {
            userIn += "\n\nStart line: " + (textSelection.getStartLine() + 1);
            userIn += "\n\nFile: " + JdtUtil.pathOf(file) 
                    + "\n\nUse tool `" + EclipseWorkspaceReadFileTool.READ_ECLIPSE_FILE_TOOL 
                    + "` only if more context is needed.";
        }
        return userIn;
    }

    private IFile getSelectedFile() {
        if (selectedResource instanceof IFile rf) return rf;
        var open = EclipseUtil.getOpenFile();
        if (open.isPresent()) return open.get();
        return null;
    }
}
