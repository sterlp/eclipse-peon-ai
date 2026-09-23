package org.sterl.llmpeon.docslinter;

/**
 * Parsed rule or use-case heading. {@code manuell} marks a ✅ status that carries a
 * manual-verification annotation (R-DL-22: exempt from the UNBELEGT check, reported
 * as an info-level MANUELL finding instead).
 */
record DocDefinition(String id, String prefix, String file, int line, int depth,
                     String status, String parentRuleId, boolean manuell) {
}