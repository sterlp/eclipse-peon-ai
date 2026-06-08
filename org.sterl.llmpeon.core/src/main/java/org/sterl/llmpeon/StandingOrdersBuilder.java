package org.sterl.llmpeon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Assembles the standing-orders system messages that are prepended each call.
 * Includes the selected resource context, any AGENTS.md content, and a plan
 * file hint when in AGENT mode.
 */
public class StandingOrdersBuilder {

    public interface MessageProvider extends Supplier<String> {}
    
    private final Set<MessageProvider> providers = new LinkedHashSet<StandingOrdersBuilder.MessageProvider>();
    private final List<String> oneTimeOrders = new ArrayList<String>();
    
    public StandingOrdersBuilder() {
        super();
    }
    public StandingOrdersBuilder add(MessageProvider provider) {
        providers.add(provider);
        return this;
    }
    
    public void addOneTimeOrder(String order) {
        this.oneTimeOrders.add(order);
    }
    
    public List<String> build() {
        
        var result = new ArrayList<String>();
        
        for (var p : providers) {
            var msg = p.get();
            if (msg != null) result.add(msg);
        }
        
        result.addAll(oneTimeOrders);
        oneTimeOrders.clear();
        
        return result;
    }
}
