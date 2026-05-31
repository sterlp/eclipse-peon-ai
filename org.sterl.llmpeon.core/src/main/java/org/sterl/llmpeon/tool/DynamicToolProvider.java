package org.sterl.llmpeon.tool;

import java.util.List;

/**
 * Provider of dynamically registered tools.
 * Called on every tool specification build — return current list of tools.
 */
@FunctionalInterface
public interface DynamicToolProvider {

    List<DynamicTool> getTools();
}
