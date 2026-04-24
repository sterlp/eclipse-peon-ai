package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
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
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog;
import org.sterl.llmpeon.parts.shared.ImageUtil;

/**
 * User input area: file chips bar (hidden until files attached), auto-resizing
 * StyledText (min 2 / max 7 rows), mic ToolItem, and Send/Stop button — all in
 * a single composite with no own border.
 */
public class UserInputWidget extends Composite {

    private static final int MAX_ROWS = 7;

    private final StyledText styledText;
    private final Composite fileChipsBar;
    private final SendOrStopButton sendOrStop;
    private ToolBar micBar;
    private ToolItem micItem;
    private final List<IFile> attachedFiles = new ArrayList<>();
    private final Color colorRecording;

    public UserInputWidget(Composite parent, int style, Runnable onSend, Runnable onStop, Runnable onMicClick) {
        super(parent, style);

        colorRecording = new Color(200, 0, 0);
        addDisposeListener(e -> colorRecording.dispose());

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
        fileChipsBar.setLayout(rowLayout);
        GridData chipsGd = new GridData(SWT.FILL, SWT.TOP, true, false);
        chipsGd.exclude = true;
        fileChipsBar.setLayoutData(chipsGd);
        fileChipsBar.setVisible(false);

        Button btnAttach = new Button(fileChipsBar, SWT.PUSH | SWT.FLAT);
        btnAttach.setText("+");
        btnAttach.setToolTipText("Attach a workspace file to this message");
        btnAttach.addListener(SWT.Selection, e -> openFilePicker());

        // --- Text row: StyledText | right button column ---
        Composite textRow = new Composite(this, SWT.NONE);
        GridLayout textRowLayout = new GridLayout(2, false);
        textRowLayout.marginWidth = 2;
        textRowLayout.marginHeight = 2;
        textRowLayout.horizontalSpacing = 2;
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

        // Right column: mic on top, send/stop at bottom
        Composite buttonBar = new Composite(textRow, SWT.NONE);
        GridLayout buttonBarLayout = new GridLayout(1, false);
        buttonBarLayout.marginWidth = 0;
        buttonBarLayout.marginHeight = 2;
        buttonBarLayout.verticalSpacing = 2;
        buttonBar.setLayout(buttonBarLayout);
        buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, false, true));

        // Mic ToolBar — hidden until voice is configured
        micBar = new ToolBar(buttonBar, SWT.FLAT);
        GridData micGd = new GridData(SWT.CENTER, SWT.TOP, false, false);
        micGd.exclude = true;
        micBar.setLayoutData(micGd);
        micBar.setVisible(false);

        micItem = new ToolItem(micBar, SWT.PUSH);
        micItem.setImage(ImageUtil.loadImage(micBar, ImageUtil.MICROPHONE));
        micItem.setToolTipText("Click to start recording — click again to stop and transcribe");
        micItem.addListener(SWT.Selection, e -> onMicClick.run());

        // Send/Stop — grabs remaining vertical space so it sits at the bottom
        sendOrStop = new SendOrStopButton(buttonBar, SWT.NONE, onSend, onStop);
        sendOrStop.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, false, true));
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

    public void setVoiceInputVisible(boolean visible) {
        boolean changed = micBar.getVisible() != visible;
        if (changed) {
            ((GridData) micBar.getLayoutData()).exclude = !visible;
            micBar.setVisible(visible);
            requestReflow();
        }
    }

    public void setRecording(boolean recording) {
        micBar.setBackground(recording ? colorRecording : null);
        micItem.setToolTipText(recording
            ? "Recording... click to stop and transcribe"
            : "Click to start recording — click again to stop and transcribe");
    }

    /** Switch the Send/Stop button between idle and working state. */
    public void setWorking(boolean working) {
        sendOrStop.setWorking(working);
    }

    @Override
    public boolean setFocus() {
        return styledText.setFocus();
    }
}
