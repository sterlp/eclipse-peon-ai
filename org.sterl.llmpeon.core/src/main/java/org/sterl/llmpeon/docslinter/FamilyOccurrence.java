package org.sterl.llmpeon.docslinter;

/**
 * Highest occurrence of one ID family (R- / UC- / flat) found in the doc set, with the
 * root-relative file it was found in (R-DL-24 Fundstelle).
 */
record FamilyOccurrence(int highestNumber, int nextNumber, String file) {
}
