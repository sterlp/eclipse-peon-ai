package org.sterl.llmpeon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Assembles the standing-orders system messages that are prepended each call.
 * Includes the selected resource context, any AGENTS.md content, and a plan
 * file hint when in AGENT mode.
 */
public class StandingOrdersBuilder {

    /** @deprecated Replaced by {@link ContextItemProvider}. */
    @Deprecated
    public interface MessageProvider extends Supplier<List<String>> {}

    public interface ContextItemProvider extends Supplier<List<ContextItem>> {}
    
    private final List<MessageProvider> messageProviders = new LinkedList<>();
    private final List<ContextItemProvider> itemProviders = new LinkedList<>();
    private final List<String> oneTimeOrders = new LinkedList<>();
    
    public StandingOrdersBuilder() {
        super();
    }

    /** @deprecated Replaced by {@link #add(ContextItemProvider)}. */
    @Deprecated
    public StandingOrdersBuilder add(MessageProvider provider) {
        messageProviders.add(provider);
        return this;
    }

    public StandingOrdersBuilder add(ContextItemProvider provider) {
        itemProviders.add(provider);
        return this;
    }
    
    public void addOneTimeOrder(String order) {
        synchronized (oneTimeOrders) {
            this.oneTimeOrders.add(order);
        }
    }
    
    /**
     * Builds the standing orders as a list of {@link ContextItem} instances.
     * One-time orders and legacy {@link MessageProvider} strings are wrapped as
     * {@link SimpleContextItem} for backward compatibility.
     */
    public List<ContextItem> buildItems() {
        var result = new ArrayList<ContextItem>();

        for (var p : itemProviders) {
            var items = p.get();
            if (items != null) {
                items.stream().filter(item -> StringUtil.hasValue(item.render()))
                    .forEach(result::add);
            }
        }

        // Legacy MessageProviders — wrap their strings as SimpleContextItem
        for (var p : messageProviders) {
            var strings = p.get();
            if (strings != null) {
                strings.stream().filter(StringUtil::hasValue)
                    .map(SimpleContextItem::new)
                    .forEach(result::add);
            }
        }

        // Atomic snapshot-and-clear for one-time orders
        var snapshot = new ArrayList<String>();
        synchronized (oneTimeOrders) {
            snapshot.addAll(oneTimeOrders);
            oneTimeOrders.clear();
        }
        snapshot.stream().filter(StringUtil::hasValue)
            .map(SimpleContextItem::new)
            .forEach(result::add);

        return result;
    }

    /** @deprecated Replaced by {@link #buildItems()}. */
    @Deprecated
    public Collection<String> build() {
        var result = new LinkedHashSet<String>();

        for (var p : messageProviders) addTo(result, p.get());

        // Atomic snapshot-and-clear: drainTo removes all elements and adds them to the target list
        var snapshot = new ArrayList<String>();
        synchronized (oneTimeOrders) {
            snapshot.addAll(oneTimeOrders);
            oneTimeOrders.clear();
        }
        addTo(result, snapshot);

        return result;
    }
    private void addTo(@NonNull LinkedHashSet<String> result,
            Collection<String> messages) {
        if (messages == null || messages.isEmpty()) return;

        messages.stream().filter(StringUtil::hasValue)
            .forEach(e -> result.add(e));
    }
}
