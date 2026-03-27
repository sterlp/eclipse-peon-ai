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
import org.sterl.llmpeon.AbstractChatService;
import org.sterl.llmpeon.AiDeveloperService;
import org.sterl.llmpeon.AiPlannerService;
import org.sterl.llmpeon.PeonMode;
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
import org.sterl.llmpeon.parts.widget.ChatWidget;
import org.sterl.llmpeon.parts.widget.StatusLineWidget;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;

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
    private final SkillService skillService = new SkillService();
    private final ToolService toolService = new ToolService();
    private final EclipseWorkspaceWriteFilesTool workspaceWriteFilesTool = new EclipseWorkspaceWriteFilesTool();
    private final EclipseWorkspaceReadFilesTool workspaceReadFilesTool = new EclipseWorkspaceReadFilesTool();
    private final AgentsMdService agentsMdService = new AgentsMdService();
    private final AtomicReference<IProgressMonitor> monitorRef = new AtomicReference<>(new NullProgressMonitor());

    private AiDeveloperService developerService;
    private AiPlannerService plannerService;
    private AgentModeService agentMode;
    private AgentModeTools agentModeTools;

    private PeonMode currentMode = PeonMode.DEV;
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

        var rootPath = ResourcesPlugin.getWorkspace().getRoot().getRawLocation().toFile().toPath();
        toolService.addTool(workspaceWriteFilesTool);
        toolService.addTool(workspaceReadFilesTool);
        toolService.addTool(new DiskFileWriteTool(rootPath));
        toolService.addTool(new DiskFileReadTool(rootPath));
        toolService.addTool(new EclipseBuildTool());
        toolService.addTool(new EclipseGrepTool());
        toolService.addTool(new EclipseRunTestTool());
        toolService.addTool(new EclipseCodeNavigationTool());

        var config = LlmPreferenceInitializer.buildWithDefaults();
        developerService = new AiDeveloperService(config, toolService, skillService, context);
        plannerService = new AiPlannerService(config, toolService, skillService, context);

        // Agent mode uses separate instances with agent-mode prompts and isolated memory
        var agentDevService = new AiDeveloperService(config, toolService, skillService, context, true);
        var agentPlanService = new AiPlannerService(config, toolService, skillService, context, true);
        agentMode = new AgentModeService(agentPlanService, agentDevService, this::doSendMessage);
        agentModeTools = new AgentModeTools(agentMode);

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
            model -> updateAllConfigs(developerService.getConfig().withModel(model)),
            autonomous -> agentMode.setAutonomous(autonomous)
        );

        applyConfig();

        if (logger != null)
            logger.info("AIChatView started: " + developerService.getConfig());

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
            agentsMdService.load(project);
            workspaceWriteFilesTool.setCurrentProject(project);
            workspaceReadFilesTool.setCurrentProject(project);
            agentMode.setProject(project);
        }

        if (actionsBar != null) Display.getDefault().asyncExec(() -> {
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
        if (selectedObjects != null && selectedObjects.length > 0) {
            setSelection(selectedObjects[0]);
        }
    }

    // -------------------------------------------------------------------------
    // EclipseAiMonitor
    // -------------------------------------------------------------------------

    @Override
    public void onMessage(SimpleMessage m) {
        Display.getDefault().asyncExec(() -> {
            chatHistory.appendMessage(m);
            actionsBar.updateCompact(getActiveService().getTokenSize(), getActiveService().getTokenWindow());
        });
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
            skillService.getSkills().size(),
            agentsMdService.hasAgentFile(),
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
            case PLAN  -> plannerService.getMessages().stream().anyMatch(m -> m.type() == ChatMessageType.AI);
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
        updateAllConfigs(config);
        refreshStatusLine();
        onConfigChanged(config);
    }

    private void updateAllConfigs(LlmConfig config) {
        developerService.updateConfig(config);
        plannerService.updateConfig(config);
        agentMode.updateConfig(config);
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
                if (!models.isEmpty() && !models.contains(config.model())) {
                    String[] items = actionsBar.getModelItems();
                    if (items.length > 0) {
                        updateAllConfigs(config.withModel(items[0]));
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
                agentMode.getActiveService().addMessage(UserMessage.from(
                        "Existing plan found:\n\n" + agentMode.readOverview()));
                agentMode.openOverviewInEditor();
                refreshChat();
            }
        } else {
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

        // PLAN -> DEV: extract last AI message and hand off to developer service
        currentMode = PeonMode.DEV;
        actionsBar.updateModeUI(PeonMode.DEV, true);

        boolean tooLarge = plannerService.getMessages().size() > 4
                && plannerService.getTokenSize() > (plannerService.getTokenWindow() * 0.4);

        AiMessage plan = null;
        if (tooLarge) {
            var messages = plannerService.getMessages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof AiMessage ai && StringUtil.hasValue(ai.text())) {
                    plan = ai;
                    break;
                }
            }
        }

        if (plan != null) developerService.clear();
        developerService.addMessage(UserMessage.from("Start Implementation"));
        if (plan != null) developerService.addMessage(plan);

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
                Display.getDefault().asyncExec(() -> actionsBar.lockWhileWorking(false));
            }
            monitor.done();
            return ex == null ? Status.OK_STATUS
                : new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, ex.getMessage(), ex);
        }).schedule();
    }

    private void doSendMessage() {
        if (StringUtil.hasNoValue(developerService.getConfig().model())) {
            chatHistory.appendMessage(new SimpleMessage(Type.PROBLEM, "No model configured — open Window > Preferences > Peon AI"));
            return;
        }
        String text = StringUtil.strip(chatInput.getText().trim() + getUserSelection());
        var active = getActiveService();
        if (StringUtil.hasNoValue(text) && active.getMessages().isEmpty()) return;

        if (StringUtil.hasValue(text)) {
            chatHistory.appendMessage(new SimpleMessage(Type.USER, text));
            chatInput.clearText();
        }

        actionsBar.lockWhileWorking(true);
        Job.create("Peon AI request", monitor -> {
            monitor.beginTask("Arbeit, Arbeit!", currentMode == PeonMode.AGENT ? ToolService.MAX_ITERATIONS * 2 : ToolService.MAX_ITERATIONS);
            monitorRef.set(monitor);
            Exception ex = null;
            try {
                int msgCountBefore = active.getMessages().size();
                active.setStandingOrders(buildStandingOrders());

                switch (currentMode) {
                    case DEV   -> developerService.call(text.isEmpty() ? null : text, this);
                    case PLAN  -> plannerService.call(text.isEmpty() ? null : text, this);
                    case AGENT -> agentMode.call(text.isEmpty() ? null : text, this);
                };

                Display.getDefault().asyncExec(() -> {
                    if (parent.isDisposed()) return;
                    if (agentMode.consumeImplementationRequest()) {
                        agentMode.startImplementation();
                        refreshChat();
                    } else if (active.getMessages().size() < msgCountBefore) {
                        refreshChat();
                    } else {
                        refreshStatusLine();
                        actionsBar.updateModeUI(currentMode, isImplEnabled());
                    }
                });
            } catch (Exception e) {
                ex = e;
                onMessage(new SimpleMessage(Type.PROBLEM, e.getMessage()));
            } finally {
                monitor.done();
                active.setStandingOrders(Collections.emptyList());
                Display.getDefault().asyncExec(() -> actionsBar.lockWhileWorking(false));
                monitorRef.set(new NullProgressMonitor());
            }
            return PeonConstants.status("Peon AI\n" + developerService.getConfig(), ex);
        }).schedule();
    }

    private void onPinChange(boolean pinned) {
        this.projectPinned = pinned;
        if (!pinned && selectedResource != null) {
            var project = EclipseUtil.resolveProject(selectedResource);
            if (project != null) {
                currentProject = project;
                agentsMdService.load(project);
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

    private AbstractChatService getActiveService() {
        return switch (currentMode) {
            case DEV   -> developerService;
            case PLAN  -> plannerService;
            case AGENT -> agentMode.getActiveService();
        };
    }

    private List<ChatMessage> buildStandingOrders() {
        var orders = new ArrayList<ChatMessage>();
        if (selectedResource != null) {
            orders.add(SystemMessage.from("Selected resource: " + JdtUtil.pathOf(selectedResource)));
        }
        if (agentsMdService.hasAgentFile()) {
            agentsMdService.agentMessage(context).ifPresent(orders::add);
        }
        if (currentMode == PeonMode.AGENT && agentMode.hasPlan()) {
            orders.add(SystemMessage.from(agentMode.planPathHint()));
        }
        return orders;
    }

    private String getUserSelection() {
        if (textSelection == null || StringUtil.hasNoValue(textSelection.getText())) return "";
        var file = getSelectedFile();

        var extension = "\n";
        if (file != null) extension = file.getFileExtension() + "\n";

        String userIn = "\n\nSelected:\n```" + extension + textSelection.getText() + "\n```";
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
