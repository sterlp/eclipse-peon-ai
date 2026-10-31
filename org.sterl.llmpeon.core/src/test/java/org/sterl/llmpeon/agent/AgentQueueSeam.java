package org.sterl.llmpeon.agent;

import org.sterl.llmpeon.queuedmessages.UserMessageQueue;

/**
 * Test-only bridge to {@link AbstractAgent}'s package-private queue seam, for tests outside this
 * package that need a queue with a deterministic clock (e.g. the Rule 11 tool-result payload test,
 * which asserts {@code (queued 14:32)} in the block). Production keeps the seam package-private.
 */
public final class AgentQueueSeam {

    private AgentQueueSeam() {}

    public static void setMessageQueue(AbstractAgent agent, UserMessageQueue queue) {
        agent.setMessageQueue(queue);
    }
}
