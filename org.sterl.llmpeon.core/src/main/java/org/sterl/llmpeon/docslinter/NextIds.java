package org.sterl.llmpeon.docslinter;

/**
 * Next-ID allocation for one prefix (R-DL-24): the found forms are continued, a missing
 * family starts at 1, and a flat registry never gets an R-/UC- form.
 */
record NextIds(String prefix, boolean occupied,
               FamilyOccurrence rule, FamilyOccurrence useCase, FamilyOccurrence flat) {

    int nextRule() {
        return rule == null ? 1 : rule.nextNumber();
    }

    int nextUseCase() {
        return useCase == null ? 1 : useCase.nextNumber();
    }

    int nextFlat() {
        return flat == null ? 1 : flat.nextNumber();
    }
}
