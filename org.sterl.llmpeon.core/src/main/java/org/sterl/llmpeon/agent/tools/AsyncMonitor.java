package org.sterl.llmpeon.agent.tools;

import java.time.Instant;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter
public class AsyncMonitor implements AiMonitor {

    private final AiMonitor root;
    private volatile SimpleMessage lastMessage;
    private volatile Instant lastMessageTime;
    @Setter
    private volatile boolean canceled = false;

    @Override
    public void onChatResponse(SimpleMessage message) {
        this.lastMessage = message;
        this.lastMessageTime = Instant.now();
    }

    @Override
    public boolean isCanceled() {
        if (canceled) return true;
        return root.isCanceled();
    }
}
