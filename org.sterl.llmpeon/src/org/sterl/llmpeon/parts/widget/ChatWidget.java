package org.sterl.llmpeon.parts.widget;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

public class ChatWidget extends Composite {

    private Text chatHistory;
    private Text inputArea;
    private ProgressBar tokenUsage;

    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
    private final ChatModel model = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("devstral-small-2:24b")
            .build();
    

    @Override
    public boolean setFocus() {
        if (inputArea != null)
            return inputArea.setFocus();
        return super.setFocus();
    }

    public ChatWidget(Composite parent, int style) {
        super(parent, style);
        createLayout();
    }

    private void createLayout() {
        setLayout(new GridLayout(1, false));

        createChatHistory(this);
        createInputArea(this);
        createCommandBar(this);
    }

    // 1️ Chat history (top)
    private void createChatHistory(Composite parent) {
        chatHistory = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    // 2️ Input area (middle, resizable)
    private void createInputArea(Composite parent) {
        inputArea = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.heightHint = 80;
        inputArea.setLayoutData(gd);

        inputArea.addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN) {
                boolean enter = (e.stateMask & SWT.CTRL) != 0 || (e.stateMask & SWT.COMMAND) != 0;

                if (enter) {
                    e.doit = false; // prevent newline
                    sendMessage();
                }
                // plain Enter → newline
            }
        });
    }

    // 3️ Command bar (bottom)
    private void createCommandBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        bar.setLayout(new GridLayout(3, false));

        Button send = new Button(bar, SWT.PUSH);
        send.setText("Send");

        tokenUsage = new ProgressBar(bar, SWT.NONE);
        tokenUsage.setMinimum(0);
        tokenUsage.setMaximum(100);
        tokenUsage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label tokenLabel = new Label(bar, SWT.NONE);
        tokenLabel.setText("0%");

        send.addListener(SWT.Selection, e -> sendMessage());
    }

    private void refreshChat() {
        StringBuilder sb = new StringBuilder();

        memory.messages().forEach(msg -> {
            if (msg instanceof UserMessage um) {
                sb.append("You: ").append(um.singleText());
            } else if (msg instanceof AiMessage am) {
                sb.append("AI: ").append(am.text());
            }
            sb.append("\n\n");
        });

        chatHistory.setText(sb.toString());
        chatHistory.setSelection(chatHistory.getCharCount()); 
        chatHistory.showSelection();
    }

    
    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty())
            return;

        memory.add(UserMessage.from(text));
        inputArea.setText("");

        Job.create("LLM request", monitor -> {
            monitor.beginTask("Calling Ollama", IProgressMonitor.UNKNOWN);

            Exception ex = null;
            try {
                var response = model.chat(memory.messages());
                memory.add(response.aiMessage());
            } catch (Exception e) {
                ex = e;
            }

            Display.getDefault().asyncExec(() -> {
                refreshChat();
                tokenUsage.setSelection(memory.messages().size() / 50); // placeholder for now
            });

            return ex == null ? Status.OK_STATUS : new Status(IStatus.ERROR, "AIChat", ex.getMessage(), ex);
        }).schedule();

        // TODO: async LLM call
        tokenUsage.setSelection(25);
    }

    public void append(String who, String what) {
        chatHistory.append(who + ":\n" + what + "\n\n");
    }
}
