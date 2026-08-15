package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.e4.ui.css.swt.CSSSWTConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.sterl.llmpeon.parts.shared.EclipseUiUtil;
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
public class UserQuestionResponseWidget extends Composite {

    public static final String CANCEL = AskUserTool.CANCEL;
    private Composite radiosContainer;
    private final TextInputWidget textInput;
    private final Runnable onSubmitDone;

    private final AtomicReference<Consumer<String>> pendingAnswer = new AtomicReference<>();

    public UserQuestionResponseWidget(Composite parent, int style, Runnable onSubmitDone) {
        super(parent, style);
        this.onSubmitDone = onSubmitDone;

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 4;
        layout.marginHeight = 4;
        layout.verticalSpacing = 4;
        setLayout(layout);

        // radiosContainer placeholder — rebuilt on each showQuestion() call
        radiosContainer = new Composite(this, SWT.NONE);
        radiosContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        radiosContainer.setLayout(new RowLayout(SWT.VERTICAL));

        // Bottom row: text input (left) + button column (right)
        Composite inputRow = new Composite(this, SWT.NONE);
        GridLayout inputRowLayout = new GridLayout(2, false);
        inputRowLayout.marginWidth = 2;
        inputRowLayout.marginHeight = 2;
        inputRowLayout.horizontalSpacing = 0;
        inputRow.setLayout(inputRowLayout);
        inputRow.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        textInput = new TextInputWidget(inputRow, SWT.NONE, 2, 7, this::requestReflow);
        textInput.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        textInput.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_USER_QUESTION_RESPONSE_WIDGET);

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

        // Right button column — cancel (top), filler (expand), answer (bottom)
        Composite rightColumn = new Composite(inputRow, SWT.NONE);
        GridLayout rcLayout = new GridLayout(1, false);
        rcLayout.marginWidth = 0;
        rcLayout.marginHeight = 4;
        rcLayout.verticalSpacing = 4;
        rightColumn.setLayout(rcLayout);
        rightColumn.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, false, true));
        rightColumn.setBackgroundMode(SWT.INHERIT_DEFAULT);
        rightColumn.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_USER_QUESTION_RESPONSE_WIDGET);

        Button cancelButton = new Button(rightColumn, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));
        cancelButton.addListener(SWT.Selection, e -> cancel());

        Label filler = new Label(rightColumn, SWT.NONE);
        GridData fillerData = new GridData(SWT.FILL, SWT.FILL, false, true);
        fillerData.heightHint = 0;
        filler.setLayoutData(fillerData);
        filler.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_USER_QUESTION_RESPONSE_WIDGET);

        Button submitButton = new Button(rightColumn, SWT.PUSH);
        submitButton.setText("Answer");
        submitButton.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, false, false));
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
    public void showQuestion(List<String> answers, Consumer<String> onAnswer) {
        pendingAnswer.set(onAnswer);

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
