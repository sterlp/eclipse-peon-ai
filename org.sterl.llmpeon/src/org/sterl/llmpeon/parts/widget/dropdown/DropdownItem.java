// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package org.sterl.llmpeon.parts.widget.dropdown;

import org.eclipse.swt.graphics.Image;

/**
 * Immutable item in a {@link DropdownButton} popup.
 *
 * @param id    unique item id (also the value delivered to the selection listener)
 * @param label display text
 * @param icon  optional leading icon, may be {@code null}
 */
public record DropdownItem(String id, String label, Image icon) {

    /** Item without icon. */
    public static DropdownItem of(String id, String label) {
        return new DropdownItem(id, label, null);
    }
}
