package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.sterl.llmpeon.parts.tools.AskUserTool;

/**
 * Inline question widget shown in place of {@link UserInputWidget} while the LLM is waiting
 * for a user answer. Displays the question, a vertical list of predefined radio-button answers
 * (plus an "Enter own answer" option), and an auto-growing text field that is the single source
 * of truth for what gets submitted.
 *
 * <p>Selecting a radio pre-fills the text field so the user can still refine the answer before
 * submitting. Submit always sends {@link TextInputWidget#getText()}.
 */
public class UserQuestionWidget extends Composite {

    public static final String CANCEL = AskUserTool.CANCEL;
    private final Label questionLabel;
    private Composite radiosContainer;
    private final TextInputWidget textInput;
    private final Runnable onSubmitDone;

    private final AtomicReference<Consumer<String>> pendingAnswer = new AtomicReference<>();

    public UserQuestionWidget(Composite parent, int style, Runnable onSubmitDone) {
        super(parent, style);
        this.onSubmitDone = onSubmitDone;

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 4;
        layout.marginHeight = 4;
        layout.verticalSpacing = 4;
        setLayout(layout);
        
        final Color bgWhite = getDisplay().getSystemColor(SWT.COLOR_WHITE);
        questionLabel = new Label(this, SWT.WRAP);
        questionLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // radiosContainer placeholder — rebuilt on each showQuestion() call
        radiosContainer = new Composite(this, SWT.NONE);
        radiosContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        radiosContainer.setLayout(new RowLayout(SWT.VERTICAL));

        // Bottom row: text input + submit button
        Composite inputRow = new Composite(this, SWT.NONE);
        GridLayout inputRowLayout = new GridLayout(3, false);
        inputRowLayout.marginWidth = 0;
        inputRowLayout.marginHeight = 0;
        inputRowLayout.horizontalSpacing = 4;
        inputRow.setLayout(inputRowLayout);
        inputRow.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        textInput = new TextInputWidget(inputRow, SWT.NONE, 7, this::requestReflow);
        textInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        textInput.setBackground(bgWhite);

        // Ctrl/Cmd+Enter submits
        textInput.addKeyListener(KeyListener.keyPressedAdapter(e -> {
            if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
                boolean send = (e.stateMask & SWT.CTRL) != 0 || (e.stateMask & SWT.COMMAND) != 0;
                if (send) {
                    e.doit = false;
                    doSubmit();
                }
            }
        }));

        Button cancelButton = new Button(inputRow, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        cancelButton.addListener(SWT.Selection, e -> cancel());

        Button submitButton = new Button(inputRow, SWT.PUSH);
        submitButton.setText("Answer");
        submitButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        submitButton.addListener(SWT.Selection, e -> doSubmit());
    }

    private void requestReflow() {
        layout(true, true);
        Composite p = getParent();
        if (p == null) return;
        p.layout(new Control[]{ this });
        Composite pp = p.getParent();
        if (pp != null) pp.layout(new Control[]{ p });
    }

    private void doSubmit() {
        String answer = textInput.getText().trim();
        Consumer<String> callback = pendingAnswer.getAndSet(null);
        if (callback != null) {
            onSubmitDone.run();
            callback.accept(answer.isEmpty() ? CANCEL : answer);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Populates and reveals the widget. Must be called on the UI thread.
     */
    public void showQuestion(String question, List<String> answers, Consumer<String> onAnswer) {
        pendingAnswer.set(onAnswer);
        questionLabel.setText(question != null ? question : "");

        // Rebuild radio buttons for this question
        for (Control c : radiosContainer.getChildren()) c.dispose();
        for (String answer : answers) {
            Button radio = new Button(radiosContainer, SWT.RADIO);
            radio.setText(answer);
            radio.addListener(SWT.Selection, e -> {
                if (radio.getSelection()) textInput.setText(answer);
            });
        }
        Button ownRadio = new Button(radiosContainer, SWT.RADIO);
        ownRadio.setText("Enter own answer");
        ownRadio.addListener(SWT.Selection, e -> {
            if (ownRadio.getSelection()) {
                textInput.clearText();
                textInput.setFocus();
            }
        });

        textInput.clearText();
        radiosContainer.layout(true, true);
        requestReflow();
        textInput.setFocus();
    }

    /**
     * Resets the widget without firing the answer callback. Must be called on the UI thread.
     */
    public void hideQuestion() {
        pendingAnswer.set(null);
        questionLabel.setText("");
        textInput.clearText();
        for (Control c : radiosContainer.getChildren()) c.dispose();
        radiosContainer.layout(true, true);
    }

    /**
     * Fires the pending answer callback with {@code "[canceled]"} and resets the widget.
     * Safe to call when no question is pending (no-op).
     */
    public void cancel() {
        Consumer<String> callback = pendingAnswer.getAndSet(null);
        if (callback != null) {
            onSubmitDone.run();
            callback.accept(CANCEL);
        }
    }

    /**
     * Releases the pending answer latch with {@code "[canceled]"} without touching any SWT
     * widgets. Safe to call from {@code @PreDestroy} when widgets may already be disposed.
     */
    public void cancelSilently() {
        Consumer<String> callback = pendingAnswer.getAndSet(null);
        if (callback != null) callback.accept(CANCEL);
    }

    public boolean setFocus() {
        return textInput.setFocus();
    }
}
