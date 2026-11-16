package org.sterl.llmpeon.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.sterl.llmpeon.agent.model.ContextInput;
import org.sterl.llmpeon.agent.model.Input;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.UserMessage;

/** Builds the one user message of a turn (R-TR-11). Stateless: no queue, no working flag. */
public final class UserMessageBuilder {

    /** R-TR-3: the key is checked BEFORE rendering (rendering reads the file); the user's payload is never deduped. */
    public List<ContextInput> contextInputs(ThreadSafeMemory memory,
            Supplier<List<ContextItem>> turnContextSupplier, AiMonitor monitor) {
        var result = new ArrayList<ContextInput>();
        if (turnContextSupplier == null) return result;
        var items = turnContextSupplier.get();
        if (items == null) return result;

        for (var item : items) {
            var key = item.dedupKey();
            if (key != null && memory.containsUserMessage(key)) continue;
            var rendered = item.render();
            if (rendered == null) continue;
            if (memory.containsMessage(rendered.toString())) continue;
            if (StringUtil.hasValue(item.label())) {
                monitor.onTool("📋 Loading " + item.label());
            }
            result.add(rendered);
        }
        return result;
    }

    /** Empty instead of an invalid, content-less UserMessage. */
    public Optional<UserMessage> build(List<ContextInput> context, UserInput input) {
        var contents = new ArrayList<Content>();
        for (var block : context) contents.addAll(block.toContents());
        contents.addAll(input.toContents());
        return contents.isEmpty() ? Optional.empty() : Optional.of(UserMessage.from(contents));
    }

    public UserMessage from(Input input) {
        return UserMessage.from(input.toContents());
    }
}
