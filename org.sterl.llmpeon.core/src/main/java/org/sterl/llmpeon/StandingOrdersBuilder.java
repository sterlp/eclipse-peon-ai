package org.sterl.llmpeon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import dev.langchain4j.data.message.SystemMessage;

/**
 * Assembles the standing-orders system messages that are prepended each call.
 * Includes the selected resource context, any AGENTS.md content, and a plan
 * file hint when in AGENT mode.
 */
public class StandingOrdersBuilder {

    /**
     * Use {@link SystemMessage} only for static messages which do not change.
     * Keep in mind any change to the message history may kill the kv cache!!
     */
    public interface MessageProvider extends Supplier<String> {}
    
    private final Set<MessageProvider> providers = new LinkedHashSet<StandingOrdersBuilder.MessageProvider>();
    
    public StandingOrdersBuilder() {
        super();
    }
    public StandingOrdersBuilder add(MessageProvider provider) {
        providers.add(provider);
        return this;
    }
    
    public List<String> build() {
        
        var result = new ArrayList<String>();
        
        for (var p : providers) {
            var msg = p.get();
            if (msg != null) result.add(msg);
        }
        
        return result;
    }
}
