package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
 * Chat input area. Outer composite is transparent (no border). A single
 * bordered inputBox contains: file chips bar, auto-resizing StyledText (up
 * to MAX_ROWS rows), mic ToolItem, and the StatusLineWidget at the bottom.
 * Fires onSend on Ctrl+Enter / Cmd+Enter.
 */
public class ChatWidget extends Composite {

    private static final int MAX_ROWS = 7;

    private final StyledText styledText;
    private final Composite fileChipsBar;
    private final Composite textRow;
    private ToolBar micBar;
    private ToolItem micItem;
    private final StatusLineWidget statusLine;
    private final List<IFile> attachedFiles = new ArrayList<>();
    private final Color colorRecording;

    public ChatWidget(Composite parent, int style, Runnable onSend, Runnable onMicClick,
            Consumer<Boolean> onPinChange) {
        super(parent, style); // no SWT.BORDER — outer is transparent

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

        // --- Bordered input box (the visual "text field") ---
        Composite inputBox = new Composite(this, SWT.BORDER);
        GridLayout inputBoxLayout = new GridLayout(1, false);
        inputBoxLayout.marginWidth = 0;
        inputBoxLayout.marginHeight = 0;
        inputBoxLayout.verticalSpacing = 0;
        inputBox.setLayout(inputBoxLayout);
        inputBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // --- Text row: StyledText + mic ToolBar ---
        textRow = new Composite(inputBox, SWT.NONE);
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

        // Mic ToolBar (flat, transparent — hidden until voice is enabled)
        micBar = new ToolBar(textRow, SWT.FLAT | SWT.RIGHT);
        GridData micGd = new GridData(SWT.RIGHT, SWT.BOTTOM, false, false);
        micGd.exclude = true;
        micBar.setLayoutData(micGd);
        micBar.setVisible(false);

        micItem = new ToolItem(micBar, SWT.PUSH);
        micItem.setImage(ImageUtil.loadImage(micBar, ImageUtil.MICROPHONE));
        micItem.setToolTipText("Click to start recording — click again to stop and transcribe");
        micItem.addListener(SWT.Selection, e -> onMicClick.run());

        // --- Status line at the bottom of the input box ---
        statusLine = new StatusLineWidget(inputBox, SWT.NONE, onPinChange);
    }

    private void refreshHeight() {
        int width = styledText.getSize().x;
        if (width <= 0) return;
        Point size = styledText.computeSize(width, SWT.DEFAULT);
        GridData gd = (GridData) styledText.getLayoutData();
        int maxHeight = styledText.getLineHeight() * MAX_ROWS;
        int newHint = Math.min(maxHeight, size.y);
        if (gd.heightHint != newHint) {
            gd.heightHint = newHint;
            layout(true, true);
            getParent().layout(new Control[]{ this });
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
        layout(true, true);
        getParent().layout(new Control[]{ this });
    }

    private void removeChip(Composite chip, IFile file) {
        attachedFiles.remove(file);
        chip.dispose();
        if (attachedFiles.isEmpty()) setChipsBarVisible(false);
        else fileChipsBar.layout(true, true);
        layout(true, true);
        getParent().layout(new Control[]{ this });
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
        layout(true, true);
        getParent().layout(new Control[]{ this });
    }

    public void setText(String text) {
        styledText.setText(text != null ? text : "");
    }

    public List<IFile> getAttachedFiles() {
        return List.copyOf(attachedFiles);
    }

    public StatusLineWidget getStatusLine() {
        return statusLine;
    }

    public void setVoiceInputVisible(boolean visible) {
        boolean changed = micBar.getVisible() != visible;
        if (changed) {
            ((GridData) micBar.getLayoutData()).exclude = !visible;
            micBar.setVisible(visible);
            layout(true, true);
            getParent().layout(new Control[]{ this });
        }
    }

    public void setRecording(boolean recording) {
        micBar.setBackground(recording ? colorRecording : null);
        micItem.setToolTipText(recording
            ? "Recording... click to stop and transcribe"
            : "Click to start recording — click again to stop and transcribe");
    }

    @Override
    public boolean setFocus() {
        return styledText.setFocus();
    }
}
