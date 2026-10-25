package org.sterl.open.aifactory.project;

/** Asked for a project the registry does not know — the API answers {@code 404}. */
public class ProjectNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProjectNotFoundException(String id) {
        super("unknown project id: " + id);
    }
}
