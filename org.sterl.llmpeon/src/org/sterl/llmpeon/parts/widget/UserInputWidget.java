package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.e4.ui.css.swt.CSSSWTConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.sterl.llmpeon.parts.shared.EclipseUiUtil;
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.parts.shared.SwtUtil;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;

/**
 * User input area: file chips bar (hidden until files attached), auto-resizing
 * StyledText, mic button, and Send/Stop button.
 *
 * <p>No backgrounds are set anywhere inside this widget — the StyledText keeps
 * its native OS white, and the paint-based Buttons from {@link SwtUtil#createIconButton}
 * inherit the same background so the whole area reads as one flat field.
 */
public class UserInputWidget extends Composite {

    private final TextInputWidget textInput;
    private final Composite rightColumn;
    private Button micButton;   // null until voice is configured
    private final Label filler;
    private final Button stopButton;
    private final Button sendButton;

    private final Image micImage;
    private final Image sendImage;  // shared registry — must NOT be disposed
    private final Image stopImage;
    private final Runnable onStop;
    private final Runnable onMicClick;

    private final Color colorRecording;

    private SlashMenuPopup slashPopup;
    private Supplier<List<SimplePromptFile>> commandSupplier;

    public UserInputWidget(Composite parent, int style, Runnable onSend, Runnable stopHandler, Runnable onMicClick) {
        super(parent, style);
        this.onStop = stopHandler;
        this.onMicClick = onMicClick;

        colorRecording = new Color(200, 0, 0);
        addDisposeListener(e -> colorRecording.dispose());

        micImage  = ImageUtil.loadImage(this, ImageUtil.MICROPHONE);
        sendImage = DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN);
        stopImage = ImageUtil.loadImage(this, ImageUtil.STOP);

        GridLayout outerLayout = new GridLayout(1, false);
        outerLayout.marginWidth = 0;
        outerLayout.marginHeight = 0;
        outerLayout.verticalSpacing = 2;
        setLayout(outerLayout);

        // --- Text row: TextInputWidget | right icon column ---
        Composite textRow = new Composite(this, SWT.NONE);
        GridLayout textRowLayout = new GridLayout(2, false);
        textRowLayout.marginWidth = 2;
        textRowLayout.marginHeight = 2;
        textRowLayout.horizontalSpacing = 0;
        textRow.setLayout(textRowLayout);
        textRow.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        textInput = new TextInputWidget(textRow, SWT.NONE, 3, 10, this::requestReflow);
        textInput.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        textInput.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_USER_QUESTION_RESPONSE_WIDGET);

        // Ctrl/Cmd+Enter sends; plain Enter inserts newline
        textInput.addKeyListener(KeyListener.keyPressedAdapter(e -> {
            if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
                boolean send = (e.stateMask & SWT.CTRL) != 0 || (e.stateMask & SWT.COMMAND) != 0;
                if (send) {
                    e.doit = false;
                    onSend.run();
                }
            }
        }));

        // Refresh the slash popup on every modification so it tracks the current prefix.
        textInput.addModifyListener(e -> refreshSlashPopup());

        // Steal arrow / Enter / Escape ONLY while the slash popup is open. Plain Enter still
        // produces a newline in StyledText when the popup is closed.
        textInput.addVerifyKeyListener(e -> {
            if (slashPopup == null || !slashPopup.isOpen()) return;
            switch (e.keyCode) {
            case SWT.ARROW_DOWN:
                slashPopup.moveSelection(1);
                e.doit = false;
                break;
            case SWT.ARROW_UP:
                slashPopup.moveSelection(-1);
                e.doit = false;
                break;
            case SWT.ESC:
                slashPopup.hide();
                e.doit = false;
                break;
            case SWT.CR:
            case SWT.LF:
            case SWT.KEYPAD_CR:
                // Plain Enter commits the selection; Ctrl/Cmd+Enter still sends the message.
                if ((e.stateMask & (SWT.CTRL | SWT.COMMAND)) == 0) {
                    if (slashPopup.commitSelection()) e.doit = false;
                }
                break;
            case SWT.TAB:
                if (slashPopup.commitSelection()) e.doit = false;
                break;
            default:
                break;
            }
        });

        // Right icon column — mic (top), spacer (fill), send (bottom). Stop sits between them.
        rightColumn = new Composite(textRow, SWT.NONE);
        GridLayout rcLayout = new GridLayout(1, false);
        rcLayout.marginWidth = 0;
        rcLayout.marginHeight = 0;
        rcLayout.verticalSpacing = 0;
        rightColumn.setLayout(rcLayout);
        rightColumn.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, false, true));
        rightColumn.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_USER_QUESTION_RESPONSE_WIDGET);
        rightColumn.setBackgroundMode(SWT.INHERIT_DEFAULT);

        // Mic button — created lazily by setVoiceInputVisible(), disposed when hidden.

        // Filler — expands vertically to push send to the bottom
        filler = new Label(rightColumn, SWT.NONE);
        GridData fillerData = new GridData(SWT.FILL, SWT.FILL, false, true);
        fillerData.heightHint = 0;
        filler.setLayoutData(fillerData);
        filler.setData(CSSSWTConstants.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_USER_QUESTION_RESPONSE_WIDGET);
        // Stop button — hidden initially, shown when working (sits at top above filler)
        stopButton = SwtUtil.createIconButton(rightColumn, stopImage, "Stop current request");
        stopButton.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, false, false));
        stopButton.setEnabled(false);
        stopButton.addListener(SWT.Selection, e -> onStop.run());

        sendButton = SwtUtil.createIconButton(rightColumn, sendImage, "Send (Ctrl+Enter)");
        sendButton.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, false, false));
        sendButton.addListener(SWT.Selection, e -> onSend.run());
    }

    private void requestReflow() {
        layout(true, true);
        Composite p = getParent();
        if (p == null) return;
        p.layout(new Control[]{ this });
        Composite pp = p.getParent();
        if (pp != null) pp.layout(new Control[]{ p });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String getText() {
        return textInput.getText();
    }

    public void clearText() {
        textInput.clearText();
        requestReflow();
    }

    public void setText(String text) {
        textInput.setText(text);
    }

    /** Show or hide the mic button. Created on show, disposed on hide so it takes zero space when gone. */
    public void setVoiceInputVisible(boolean visible) {
        if (visible && (micButton == null || micButton.isDisposed())) {
            micButton = SwtUtil.createIconButton(rightColumn, micImage, "Click to start recording");
            micButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));
            // Insert at the very top of the column (before stop button)
            micButton.moveAbove(filler);
            micButton.addListener(SWT.Selection, e -> onMicClick.run());
        } else if (!visible && micButton != null && !micButton.isDisposed()) {
            micButton.dispose();
            micButton = null;
        }
        rightColumn.layout(true, true);
        requestReflow();
    }

    public void setRecording(boolean recording) {
        if (micButton != null && !micButton.isDisposed()) {
            micButton.setBackground(recording ? colorRecording : null);
            micButton.setToolTipText(recording
                ? "Recording... click to stop and transcribe"
                : "Click to start recording — click again to stop and transcribe");
            micButton.redraw();
        }
    }

    public void isWorking(boolean working) {
        stopButton.setEnabled(working);
        //rightColumn.layout(true, true);
    }

    @Override
    public boolean setFocus() {
        return textInput.setFocus();
    }

    /** Hides the slash-command popup if it is currently shown. Safe to call any time. */
    public void dismissSlashMenu() {
        if (slashPopup != null) slashPopup.hide();
    }

    /**
     * Enables the slash-command popup. {@code commandSupplier} returns the currently loaded
     * commands. Pass {@code null} to disable the popup.
     */
    public void enableSlashCommands(Supplier<List<SimplePromptFile>> commandSupplier) {
        this.commandSupplier = commandSupplier;
        if (commandSupplier == null) {
            disposeSlashPopup();
            return;
        }
        if (slashPopup == null) {  // only enters here once...
            slashPopup = new SlashMenuPopup(this, this::applyCommandSelection);
            addDisposeListener(e -> disposeSlashPopup()); // ...so this is safe
        }
    }

    private void refreshSlashPopup() {
        if (commandSupplier == null || slashPopup == null) return;
        var text = textInput.getText();
        if (text == null || !text.startsWith("/") || hasWhitespaceAfterSlash(text)) {
            slashPopup.hide();
            return;
        }
        var commands = commandSupplier.get();
        if (commands == null || commands.isEmpty()) { slashPopup.hide(); return; }
        slashPopup.show(commands, extractPrefix(text), textInput.getCaretDisplayLocation());
    }

    private static boolean hasWhitespaceAfterSlash(String text) {
        for (int i = 1; i < text.length(); i++)
            if (Character.isWhitespace(text.charAt(i))) return true;
        return false;
    }

    private static String extractPrefix(String text) {
        // Read characters after the leading '/' until whitespace.
        var sb = new StringBuilder();
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) break;
            sb.append(c);
        }
        return sb.toString();
    }

    private void applyCommandSelection(SimplePromptFile cmd) {
        String replacement = "/" + cmd.getName() + " ";
        textInput.setText(replacement);
        textInput.setCaretOffset(replacement.length()); // cursor after the space
        textInput.setFocus();
    }

    private void disposeSlashPopup() {
        if (slashPopup != null) {
            slashPopup.dispose();
            slashPopup = null;
        }
    }
}
