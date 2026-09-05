package org.sterl.llmpeon.parts.widget;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.osgi.framework.FrameworkUtil;
import org.sterl.llmpeon.parts.shared.EclipseUiUtil;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.LiveStatus;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.OnPartialAiResponse.Type;
import org.sterl.llmpeon.parts.widget.model.HideLiveStatusCommand;
import org.sterl.llmpeon.parts.widget.model.LiveStatusCommand;
import org.sterl.llmpeon.parts.widget.model.SetThemeCommand;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.ToSimpleMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;

public class ChatMarkdownWidget extends Composite {

    private final Browser browser;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private String chatHtml = null;

    // volatile longs, UI-thread contract (see AIChatView.onStreamingChunk).
    private volatile long estimatedTokens = 0;
    private volatile long lastRenderedTokens = 0;
    private volatile long lastTokenPhaseStart = 0;
    private final Composite parent;
    private volatile boolean showRealtimeAiResponse = false;
    private final StringBuilder thinkText = new StringBuilder();
    private final StringBuilder answerText = new StringBuilder();

    private volatile boolean browserReady = false;
    private final java.util.Queue<String> pendingMessages = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile String currentTheme = "light";

    public ChatMarkdownWidget(Composite parent, int style) {
        super(parent, style);
        this.parent = parent;
        setLayout(new FillLayout());

        browser = new Browser(this, SWT.NONE);

        // BrowserFunction is not reliably supported by all SWT browser engines
        // (e.g. WebKit2 on Linux/GTK),
        // so we use title changes from JavaScript (document.title =
        // "javaReady") as a ready signal instead.
        browser.addTitleListener(new TitleListener() {
            @Override
            public void changed(TitleEvent event) {
                if ("javaReady".equals(event.title)) {
                    EclipseUtil.runInUiThread(parent, () -> {
                        browserReady = true;
                        String json;
                        while ((json = pendingMessages.poll()) != null) {
                            browser.execute("window.dispatchEvent(new MessageEvent('message', {data: " + json + "}));");
                        }
                    });
                }
            }
        });

        browser.addLocationListener(new LocationListener() {
            @Override
            public void changing(LocationEvent event) {
                final String prefix = "open-in-editor:";
                if (event.location == null
                        || !event.location.startsWith(prefix))
                    return;
                event.doit = false;
                var path = URLDecoder.decode(
                        event.location.substring(prefix.length()),
                        StandardCharsets.UTF_8);
                var resolved = EclipseUtil.resolveInEclipse(path);
                if (resolved.filter(IFile.class::isInstance)
                        .map(IFile.class::cast)
                        .filter(f -> !EclipseUtil.isOpenInEditor(f))
                        .isPresent()) {
                    EclipseUtil.openInEditor((IFile) resolved.get());
                } else {
                    // Fallback: search by filename
                    var fileName = java.nio.file.Path.of(path).getFileName()
                            .toString();
                    EclipseUtil.searchWorkspaceFiles(fileName)
                            .ifPresent(file -> {
                                if (!EclipseUtil.isOpenInEditor(file))
                                    EclipseUtil.openInEditor(file);
                            });
                    if (!EclipseUtil.searchWorkspaceFiles(fileName)
                            .isPresent()) {
                        postMessage(new SimpleMessage(SimpleMessage.Type.PROBLEM, "File not found: " + fileName));
                    }
                }
            }

            @Override
            public void changed(LocationEvent event) {
                // no-op
            }
        });

        EclipseUiUtil.addThemeChangeListener(theme -> {
            currentTheme = theme;
            postMessage("light".equals(theme) ? SetThemeCommand.LIGHT : SetThemeCommand.DARK);
        });

        clear();
    }

    private String loadChatHtml() {
        if (chatHtml != null)
            return chatHtml;
        try (InputStream is = getClass()
                .getResourceAsStream("/resources/chat/chat.html")) {
            if (is == null) {
                throw new RuntimeException("chat.html not found on classpath");
            }
            var loaded = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            chatHtml = resolveResourcePaths(loaded);
            return chatHtml;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load chat.html", e);
        }
    }

    /**
     * Replaces all relative {@code ./} paths in the HTML with absolute file://
     * URLs so the embedded browser can load CSS, JS, and language files.
     */
    private String resolveResourcePaths(String html) throws IOException {
        URL chatDir = FileLocator.find(FrameworkUtil.getBundle(getClass()),
                new Path("resources/chat/"), null);
        if (chatDir == null) {
            throw new IOException(
                    "resources/chat/ directory not found in bundle");
        }
        String basePath = FileLocator.toFileURL(chatDir).toString();
        // all resources use ./ relative paths, so a single replace resolves
        // everything
        return html.replace("./", basePath);
    }

    /** Send JSON payload to the browser via MessageEvent — identical to test harness approach. */
    private void postMessage(Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            if (browserReady) {
                browser.execute("window.dispatchEvent(new MessageEvent('message', {data: " + json + "}));");
            } else {
                pendingMessages.add(json);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void setShowRealtimeAiResponse(boolean show) {
        this.showRealtimeAiResponse = show;
    }

    public boolean isShowRealtimeAiResponse() {
        return showRealtimeAiResponse;
    }

    public void appendMessage(SimpleMessage msg) {
        postMessage(msg);
    }

    public void hideLiveStatus() {
        postMessage(HideLiveStatusCommand.INSTANCE);
    }

    public void onStreamingChunk(OnPartialAiResponse r) {
        switch (r.type()) {
            case START -> {
                estimatedTokens = 0;
                lastRenderedTokens = 0;
                lastTokenPhaseStart = 0;
            }
            case THINK -> {
                thinkText.append(r.value());
                estimatedTokens += ChatMessageUtil.estimateTokens(r.value());
            }
            case ANSWER -> {
                answerText.append(r.value());
                estimatedTokens += ChatMessageUtil.estimateTokens(r.value());
            }
            case TOOL -> estimatedTokens += ChatMessageUtil.estimateTokens(r.value());
            case END -> EclipseUtil.runInUiThread(parent, this::hideLiveStatus);
        }
        if (r.type() != Type.END) updateRunningChunk(r);
    }

    private void updateRunningChunk(OnPartialAiResponse r) {
        LiveStatus status = LiveStatus.of(r, estimatedTokens, System.currentTimeMillis(), lastTokenPhaseStart); // R22: read BEFORE write — LiveStatus.of needs the previous value
        if (r.tokenPhaseStart() != 0) lastTokenPhaseStart = r.tokenPhaseStart();
        if (r.type() == Type.START) {
            thinkText.setLength(0);
            answerText.setLength(0);
            updateLiveResponseInUIThread(status.state(), status.tokPerSec(), "");
            return;
        }
        // render the first chunk immediately, then throttle to every 20 new tokens
        long delta = estimatedTokens - lastRenderedTokens;
        if (lastRenderedTokens == 0 || delta >= 20) {
            lastRenderedTokens = estimatedTokens;
            String accumulatedText = switch (r.type()) {
                case THINK -> showRealtimeAiResponse ? thinkText.toString() : estimatedTokens + " tokens";
                case ANSWER -> showRealtimeAiResponse ? answerText.toString() : estimatedTokens + " tokens";
                default -> estimatedTokens + " tokens";
            };
            updateLiveResponseInUIThread(status.state(), status.tokPerSec(), accumulatedText);
        }
    }

    public void updateLiveResponseInUIThread(String state, double tokPerSec, String safeChunk) {
        EclipseUtil.runInUiThread(parent, () -> {
            postMessage(new LiveStatusCommand(state, tokPerSec, safeChunk));
        });
    }

    public void showDiff(String unifiedDiff) {
        postMessage(new SimpleMessage(SimpleMessage.Type.DIFF, unifiedDiff));
    }

    public void clear() {
        this.browserReady = false;
        this.pendingMessages.clear();
        browser.setText(loadChatHtml());
        currentTheme = EclipseUiUtil.resolveTheme();
        postMessage("light".equals(currentTheme) ? SetThemeCommand.LIGHT : SetThemeCommand.DARK);
    }

    public void appendMessage(ChatMessage msg) {
        var toAdd = ToSimpleMessage.INSTANCE.convert(msg);
        toAdd.forEach(this::appendMessage);
    }
}
