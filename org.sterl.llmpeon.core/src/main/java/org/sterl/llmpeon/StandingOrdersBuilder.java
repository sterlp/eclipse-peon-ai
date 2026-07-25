package org.sterl.llmpeon;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Assembles the standing-orders system messages that are prepended each call.
 * Includes the selected resource context, any AGENTS.md content, and a plan
 * file hint when in AGENT mode.
 */
public class StandingOrdersBuilder {

    public interface MessageProvider extends Supplier<List<String>> {}
    
    private final List<MessageProvider> providers = new LinkedList<>();
    private final List<String> oneTimeOrders = new LinkedList<>();
    
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
    
    public Collection<String> build() {

        var result = new LinkedHashSet<String>();

        for (var p : providers) addTo(result, p.get());

        addTo(result, oneTimeOrders);
        oneTimeOrders.clear();

        return result;
    }
    private void addTo(@NonNull LinkedHashSet<String> result,
            Collection<String> messages) {
        if (messages == null || messages.isEmpty()) return;
        
        messages.stream().filter(StringUtil::hasValue)
            .forEach(e -> result.add(e));
    }
}
