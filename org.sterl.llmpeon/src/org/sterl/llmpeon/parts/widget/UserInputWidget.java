package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog;
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.parts.shared.SwtUtil;

/**
 * User input area: file chips bar (hidden until files attached), auto-resizing
 * StyledText (min 2 / max 7 rows), mic button, and Send/Stop button.
 *
 * <p>No backgrounds are set anywhere inside this widget — the StyledText keeps
 * its native OS white, and the paint-based Buttons from {@link SwtUtil#createIconButton}
 * inherit the same background so the whole area reads as one flat field.
 */
public class UserInputWidget extends Composite {

    private static final int MAX_ROWS = 7;

    private final StyledText styledText;
    private final Composite fileChipsBar;
    private final Composite rightColumn;
    private final Button sendButton;
    private Button micButton;   // null until voice is configured

    private final Image micImage;
    private final Image sendImage;  // shared registry — must NOT be disposed
    private final Image stopImage;
    private boolean working = false;
    private final Runnable onMicClick;

    private final List<IFile> attachedFiles = new ArrayList<>();
    private final Color colorRecording;

    public UserInputWidget(Composite parent, int style, Runnable onSend, Runnable onStop, Runnable onMicClick) {
        super(parent, style);
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

        // --- File chips bar (hidden until files are attached) ---
        fileChipsBar = new Composite(this, SWT.NONE);
        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.wrap = true;
        rowLayout.pack = true;
        rowLayout.center = true;
        rowLayout.marginTop = 0;
        rowLayout.marginBottom = 2;
        rowLayout.marginLeft = 0;
        fileChipsBar.setLayout(rowLayout);
        GridData chipsGd = new GridData(SWT.FILL, SWT.TOP, true, false);
        chipsGd.exclude = true;
        fileChipsBar.setLayoutData(chipsGd);
        fileChipsBar.setVisible(false);

        Button btnAttach = new Button(fileChipsBar, SWT.PUSH | SWT.FLAT);
        btnAttach.setText("+");
        btnAttach.setToolTipText("Attach a workspace file to this message");
        btnAttach.addListener(SWT.Selection, e -> openFilePicker());

        // --- Text row: StyledText | right icon column ---
        Composite textRow = new Composite(this, SWT.NONE);
        GridLayout textRowLayout = new GridLayout(2, false);
        textRowLayout.marginWidth = 2;
        textRowLayout.marginHeight = 2;
        textRowLayout.horizontalSpacing = 0;
        textRow.setLayout(textRowLayout);
        textRow.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        styledText = new StyledText(textRow, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        styledText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        styledText.addModifyListener(e -> refreshHeight());

        // Ctrl/Cmd+Enter sends; plain Enter inserts newline
        styledText.addKeyListener(KeyListener.keyPressedAdapter(e -> {
            if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
                boolean send = (e.stateMask & SWT.CTRL) != 0 || (e.stateMask & SWT.COMMAND) != 0;
                if (send) {
                    e.doit = false;
                    onSend.run();
                }
            }
        }));

        // Right icon column — mic on top (optional), send/stop at bottom
        rightColumn = new Composite(textRow, SWT.NONE);
        GridLayout rcLayout = new GridLayout(1, false);
        rcLayout.marginWidth = 0;
        rcLayout.marginHeight = 0;
        rcLayout.verticalSpacing = 0;
        rightColumn.setLayout(rcLayout);
        rightColumn.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, false, true));

        sendButton = SwtUtil.createIconButton(rightColumn, sendImage, "Send (Ctrl+Enter)");
        sendButton.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, false, true));
        sendButton.addListener(SWT.Selection, e -> {
            if (working) onStop.run();
            else onSend.run();
        });
    }

    private void requestReflow() {
        layout(true, true);
        Composite p = getParent();
        if (p == null) return;
        p.layout(new Control[]{ this });
        Composite pp = p.getParent();
        if (pp != null) pp.layout(new Control[]{ p });
    }

    private void refreshHeight() {
        int width = styledText.getSize().x;
        if (width <= 0) return;
        Point size = styledText.computeSize(width, SWT.DEFAULT);
        GridData gd = (GridData) styledText.getLayoutData();
        int lineH = styledText.getLineHeight();
        int minHeight = lineH * 2;
        int maxHeight = lineH * MAX_ROWS;
        int newHint = Math.max(minHeight, Math.min(maxHeight, size.y));
        if (gd.heightHint != newHint) {
            gd.heightHint = newHint;
            requestReflow();
        }
    }

    private void openFilePicker() {
        var dialog = new FilteredResourcesSelectionDialog(
                getShell(), true,
                ResourcesPlugin.getWorkspace().getRoot(),
                IResource.FILE);
        dialog.setTitle("Attach Files");
        if (dialog.open() == Dialog.OK) {
            for (Object result : dialog.getResult()) {
                if (result instanceof IFile file && !attachedFiles.contains(file)) {
                    attachedFiles.add(file);
                    addFileChip(file);
                }
            }
        }
    }

    private void addFileChip(IFile file) {
        Composite chip = new Composite(fileChipsBar, SWT.NONE);
        GridLayout chipLayout = new GridLayout(3, false);
        chipLayout.marginWidth = 3;
        chipLayout.marginHeight = 1;
        chipLayout.horizontalSpacing = 2;
        chip.setLayout(chipLayout);

        Label icon = new Label(chip, SWT.NONE);
        ImageDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry()
                .getImageDescriptor(file.getName());
        if (desc == null) {
            desc = PlatformUI.getWorkbench().getSharedImages()
                    .getImageDescriptor(ISharedImages.IMG_OBJ_FILE);
        }
        Image fileImage = desc.createImage();
        icon.setImage(fileImage);
        icon.addDisposeListener(e -> fileImage.dispose());

        Label name = new Label(chip, SWT.NONE);
        name.setText(file.getName());
        name.setToolTipText(file.getFullPath().toString());

        Button remove = new Button(chip, SWT.PUSH | SWT.FLAT);
        remove.setText("×");
        remove.setToolTipText("Remove " + file.getName());
        remove.addListener(SWT.Selection, e -> removeChip(chip, file));

        setChipsBarVisible(true);
        fileChipsBar.layout(true, true);
        requestReflow();
    }

    private void removeChip(Composite chip, IFile file) {
        attachedFiles.remove(file);
        chip.dispose();
        if (attachedFiles.isEmpty()) setChipsBarVisible(false);
        else fileChipsBar.layout(true, true);
        requestReflow();
    }

    private void setChipsBarVisible(boolean visible) {
        ((GridData) fileChipsBar.getLayoutData()).exclude = !visible;
        fileChipsBar.setVisible(visible);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String getText() {
        return styledText.getText();
    }

    public void clearText() {
        styledText.setText("");
        for (Control c : fileChipsBar.getChildren()) {
            if (c instanceof Composite) c.dispose();
        }
        attachedFiles.clear();
        setChipsBarVisible(false);
        requestReflow();
    }

    public void setText(String text) {
        styledText.setText(text != null ? text : "");
    }

    public List<IFile> getAttachedFiles() {
        return List.copyOf(attachedFiles);
    }

    /** Show or hide the mic button. Created on first show, disposed on hide so the slot is truly empty. */
    public void setVoiceInputVisible(boolean visible) {
        if (visible && (micButton == null || micButton.isDisposed())) {
            micButton = SwtUtil.createIconButton(rightColumn,
                    micImage,
                    "Click to start recording — click again to stop and transcribe");
            // Place mic before sendButton and sit at the top
            micButton.moveAbove(sendButton);
            micButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));
            micButton.addListener(SWT.Selection, e -> onMicClick.run());
            rightColumn.layout(true, true);
            requestReflow();
        } else if (!visible && micButton != null && !micButton.isDisposed()) {
            micButton.dispose();
            micButton = null;
            rightColumn.layout(true, true);
            requestReflow();
        }
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

    /** Switch the Send/Stop button between idle and working state. */
    public void setWorking(boolean working) {
        this.working = working;
        if (working) {
            sendButton.setImage(stopImage);
            sendButton.setToolTipText("Cancel current request");
        } else {
            sendButton.setImage(sendImage);
            sendButton.setToolTipText("Send (Ctrl+Enter)");
        }
        sendButton.redraw();
    }

    @Override
    public boolean setFocus() {
        return styledText.setFocus();
    }
}
