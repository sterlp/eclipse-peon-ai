package org.sterl.llmpeon.docslinter;

record DocDefinition(String id, String prefix, String file, int line, int depth,
                     String status, String parentRuleId) {
}