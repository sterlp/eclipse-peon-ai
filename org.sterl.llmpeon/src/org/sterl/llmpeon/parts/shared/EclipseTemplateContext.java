package org.sterl.llmpeon.parts.shared;

import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.ITextSelection;
import org.sterl.llmpeon.template.TemplateContext;

/**
 * Eclipse-specific {@link TemplateContext} that adds workspace variables.
 *
 * <h2>Eclipse-specific Variables</h2>
 * <ul>
 *   <li>{@link #CURRENT_SELECTED_FILE} — {@code ${currentSelectedFile}}</li>
 *   <li>{@link #CURRENT_PROJECT}       — {@code ${currentProject}}</li>
 *   <li>{@link #SELECTED_TEXT}         — {@code ${selectedText}}</li>
 * </ul>
 *
 * <p>Generic variables ({@link TemplateContext#CURRENT_DATE}, {@link TemplateContext#WORK_PATH})
 * are inherited from the parent class.
 *
 * @see <a href="https://peon-ai-4e.sterl.org/setup/template-variables/">Template Variables Documentation</a>
 */
public class EclipseTemplateContext extends TemplateContext {

    /** Eclipse-portable path of the currently selected resource, e.g. {@code /my-project/src/Foo.java} */
    public static final String CURRENT_SELECTED_FILE = "currentSelectedFile";

    /** Name of the Eclipse project that contains the selected resource, e.g. {@code my-project} */
    public static final String CURRENT_PROJECT = "currentProject";

    /** Text currently selected in the editor (empty when nothing is selected) */
    public static final String SELECTED_TEXT = "selectedText";

    private IResource selectedResource;
    private ITextSelection textSelection;

    public EclipseTemplateContext() {
        super(ResourcesPlugin.getWorkspace().getRoot().getRawLocation().toFile().toPath());
    }

    @Override
    public Map<String, String> build() {
        var ctx = super.build();
        ctx.put(CURRENT_SELECTED_FILE, selectedResource != null
                ? selectedResource.getFullPath().toPortableString() : "");
        ctx.put(CURRENT_PROJECT, selectedResource != null
                ? selectedResource.getProject().getName() : "");
        ctx.put(SELECTED_TEXT, textSelection != null && !textSelection.isEmpty()
                ? textSelection.getText() : "");
        return ctx;
    }

    public IResource getSelectedResource() {
        return selectedResource;
    }

    public void setSelectedResource(IResource selectedResource) {
        this.selectedResource = selectedResource;
    }

    public ITextSelection getTextSelection() {
        return textSelection;
    }

    public void setTextSelection(ITextSelection textSelection) {
        this.textSelection = textSelection;
    }
}
