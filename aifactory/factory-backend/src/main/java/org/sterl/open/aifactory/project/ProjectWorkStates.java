package org.sterl.open.aifactory.project;

import java.util.List;

/**
 * The only way {@code project} learns what is running (D-6): {@code chat} implements it, so the knowledge
 * stays in one direction and the guard of one conversation owner is untouched.
 *
 * <p>Read from memory, never stored: an entry the server holds no runtime for answers
 * {@link ProjectWork#free(String)}, which is what makes every project free after a restart
 * (R-WP-24).</p>
 */
public interface ProjectWorkStates {

    /** The state of every registered project, in the order of the registry. */
    List<ProjectWork> current();
}
