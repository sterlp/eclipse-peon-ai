package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
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
import org.sterl.llmpeon.AbstractChatService;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpPreferenceInitializer;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.shared.SimpleDiff;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.widget.ActionsBarWidget;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget;
import org.sterl.llmpeon.parts.widget.ChatWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class AIChatView implements EclipseAiMonitor {

    @Inject
    Logger logger;

    // Declared first so the aiService field initializer lambdas can capture them
    // without violating the Java forward-reference restriction.
    // Both are null until @PostConstruct runs; the lambdas are only ever invoked after that.
    private Composite parent;
    private ActionsBarWidget actionsBar;

    private final PeonAiService aiService = new PeonAiService(
        this::doSendMessage,
        file -> EclipseUtil.runInUiThread(parent, () -> EclipseUtil.openInEditor(file)),
        enabled -> EclipseUtil.runInUiThread(parent, () -> actionsBar.setMcpEnabled(enabled))
    );

    private final AtomicReference<IProgressMonitor> monitorRef = new AtomicReference<>(new NullProgressMonitor());

    private PeonMode currentMode = PeonMode.DEV;
    private LlmConfig lastListedConfig;
    private IProject currentProject;
    private boolean projectPinned = false;

    private ChatMarkdownWidget chatHistory;
    private ChatWidget chatInput;
    private StatusLineWidget statusLine;

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

        chatInput = new ChatWidget(parent, SWT.NONE, this::doSendMessage);
        chatInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLine = new StatusLineWidget(parent, SWT.NONE, this::onPinChange);

        actionsBar = new ActionsBarWidget(parent, SWT.NONE,
            this::doSendMessage,
            () -> getIProgressMonitor().setCanceled(true),
            this::doCompressContext,
            this::onClear,
            this::doStartImpl,
            this::onModeChange,
            model -> aiService.updateConfig(aiService.getDeveloperService().getConfig().withModel(model)),
            autonomous -> aiService.getAgentMode().setAutonomous(autonomous),
            enabled -> aiService.getMcpConnectionService().toggle(enabled)
        );

        applyConfig();

        if (logger != null)
            logger.info("AIChatView started: " + aiService.getDeveloperService().getConfig());

        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        prefs.addPreferenceChangeListener(prefListener);

        updateSelectedProject(EclipseUtil.firstOpenOrSelectedProject());
    }

    private void onClear() {
        getActiveService().clear();
        chatHistory.clear();
        actionsBar.updateCompact(getActiveService().getTokenSize(), getActiveService().getTokenWindow());
    }

    @PreDestroy
    public void dispose() {
        InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID).removePreferenceChangeListener(prefListener);
        aiService.disconnectMcp();
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
            System.err.println("!!! Unknown resource type selected " + o.getClass());
            selection = selectedResource;
        } else {
            selection = null;
        }
        selectedResource = selection;
        if (selectedResource != null) {
            System.out.println("Selected " + selectedResource.getName());
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
            actionsBar.updateCompact(getActiveService().getTokenSize(), getActiveService().getTokenWindow());
        });
    }

    @Override
    public void onCallCompleted(dev.langchain4j.model.chat.response.ChatResponse response, Duration duration) {
        EclipseUtil.runInUiThread(parent, () -> {
            actionsBar.lockWhileWorking(false);
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
            aiService.getSkillService().getSkills().size(),
            aiService.getAgentsMdService().hasAgentFile(),
            currentProject,
            getSelectedFile()
        );
        var active = getActiveService();
        actionsBar.updateCompact(active.getTokenSize(), active.getTokenWindow());
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
            aiService.getSkillService().refresh(config.skillDirectory());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + config.skillDirectory());
        }
        aiService.updateConfig(config);
        applyMcpConfig();
        refreshStatusLine();
        onConfigChanged(config);
    }

    private void applyMcpConfig() {
        var servers = McpPreferenceInitializer.loadServers();
        actionsBar.setMcpAvailable(!servers.isEmpty());
        actionsBar.setMcpEnabled(!servers.isEmpty() && McpPreferenceInitializer.isMcpEnabled());
        aiService.applyMcpConfig();
    }

    private void onConfigChanged(LlmConfig config) {
        if (lastListedConfig == null
                || config.providerType() != lastListedConfig.providerType()
                || !java.util.Objects.equals(config.url(), lastListedConfig.url())
                || !java.util.Objects.equals(config.apiKey(), lastListedConfig.apiKey())) {
            loadModelsInBackground(config);
        } else {
            EclipseUtil.runInUiThread(parent, () -> {
                if (parent.isDisposed()) return;
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
            EclipseUtil.runInUiThread(parent, () -> {
                actionsBar.applyModelList(models, config.model());
                if (!models.isEmpty() && !models.contains(config.model())) {
                    String[] items = actionsBar.getModelItems();
                    if (items.length > 0) {
                        aiService.updateConfig(config.withModel(items[0]));
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

    private void doStartImpl() {
        if (currentMode == PeonMode.AGENT) {
            aiService.getAgentMode().startImplementation();
            refreshChat();
            return;
        }

        // PLAN -> DEV: extract last AI message and hand off to developer service
        currentMode = PeonMode.DEV;
        actionsBar.updateModeUI(PeonMode.DEV, true);

        var plan = aiService.getPlannerService().extractLastPlan();
        if (plan.isPresent()) aiService.getDeveloperService().clear();
        aiService.getDeveloperService().addMessage(UserMessage.from("Start Implementation"));
        plan.ifPresent(aiService.getDeveloperService()::addMessage);

        refreshChat();
        doSendMessage();
    }

    private void doCompressContext() {
        chatHistory.clear();

        var active = getActiveService();
        if (active.getMessages().isEmpty()) return;
        actionsBar.lockWhileWorking(true);
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
                EclipseUtil.runInUiThread(parent, () -> actionsBar.lockWhileWorking(false));
            }
            monitor.done();
            return ex == null ? Status.OK_STATUS
                : new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, ex.getMessage(), ex);
        }).schedule();
    }

    private void doSendMessage() {
        if (StringUtil.hasNoValue(aiService.getDeveloperService().getConfig().model())) {
            chatHistory.appendMessage(new SimpleMessage(Type.PROBLEM, "No model configured — open Window > Preferences > Peon AI"));
            return;
        }
        String text = StringUtil.strip(chatInput.getText().trim() + getUserSelection());
        var active = getActiveService();
        if (StringUtil.hasNoValue(text) && active.getMessages().isEmpty()) return;

        if (StringUtil.hasValue(text)) {
            chatHistory.appendMessage(new SimpleMessage(Type.USER, text));
            chatInput.clearText();

            // are already working -> we only append the text message to the current history ...
            if (actionsBar.isWorking()) {
                getActiveService().addMessage(UserMessage.from(text));
                return;
            }
        }

        actionsBar.lockWhileWorking(true);
        Job.create("Peon AI request", monitor -> {
            monitor.beginTask("Arbeit, Arbeit!", currentMode == PeonMode.AGENT ? ToolService.MAX_ITERATIONS * 2 : ToolService.MAX_ITERATIONS);
            monitorRef.set(monitor);
            Exception ex = null;
            try {
                active.setStandingOrders(StandingOrdersBuilder.build(
                        selectedResource, aiService.getAgentsMdService(), aiService.getTemplateContext(),
                        currentMode, aiService.getAgentMode()));
                switch (currentMode) {
                    case DEV   -> aiService.getDeveloperService().call(text.isEmpty() ? null : text, this);
                    case PLAN  -> aiService.getPlannerService().call(text.isEmpty() ? null : text, this);
                    case AGENT -> aiService.getAgentMode().call(text.isEmpty() ? null : text, this);
                };
            } catch (Exception e) {
                ex = e;
                onChatResponse(new SimpleMessage(Type.PROBLEM, e.getMessage()));
                EclipseUtil.runInUiThread(parent, () -> actionsBar.lockWhileWorking(false));
            } finally {
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

        String userIn = "\n\nSelected:\n```" + extension + textSelection.getText() + "\n```";
        userIn += "\n\nStart line: " + (textSelection.getStartLine() + 1);

        if (file != null) userIn += "\nFile: " + JdtUtil.pathOf(file) + " use "
                + EclipseWorkspaceReadFileTool.READ_ECLIPSE_FILE_TOOL + " to read whole file, if needed.";
        return userIn;
    }

    private IFile getSelectedFile() {
        if (selectedResource instanceof IFile rf) return rf;
        var open = EclipseUtil.getOpenFile();
        if (open.isPresent()) return open.get();
        return null;
    }
}
