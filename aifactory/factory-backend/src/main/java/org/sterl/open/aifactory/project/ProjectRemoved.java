package org.sterl.open.aifactory.project;

/**
 * The fact that a project is gone — registry entry and checkout. Published by
 * {@link ProjectService#remove(String)} after both are deleted, so a component that kept per-project
 * state can let go of it without this package knowing who keeps anything: there is no path
 * {@code project → chat} (architecture doc "Grenzen der Komponenten").
 */
public record ProjectRemoved(String projectId) {
}
