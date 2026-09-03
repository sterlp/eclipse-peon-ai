package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.sterl.llmpeon.parts.widget.dropdown.DropdownItem;

/**
 * Pure unit test for the {@link DropdownItem} record (no display needed).
 */
public class DropdownItemTest extends AbstractUnitTest {

    @Test
    public void equalityIsValueBased() {
        // GIVEN two items with identical fields
        var a = DropdownItem.of("agent-1", "Peon-Dev");
        var b = DropdownItem.of("agent-1", "Peon-Dev");

        // THEN they are equal and hash the same (record semantics)
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void differentIdOrLabelBreaksEquality() {
        var base = DropdownItem.of("agent-1", "Peon-Dev");

        assertNotEquals(base, DropdownItem.of("agent-2", "Peon-Dev"));
        assertNotEquals(base, DropdownItem.of("agent-1", "Peon-Plan"));
    }

    @Test
    public void ofFactoryLeavesIconNull() {
        // GIVEN an item built via the icon-less factory
        var item = DropdownItem.of("model-1", "gpt-4o");

        // THEN its icon is null (nullable by design) and the components are exposed
        assertNull(item.icon());
        assertEquals("model-1", item.id());
        assertEquals("gpt-4o", item.label());
    }
}
