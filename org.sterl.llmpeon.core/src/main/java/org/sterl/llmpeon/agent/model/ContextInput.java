package org.sterl.llmpeon.agent.model;

import java.util.Objects;

/** A block the harness adds as turn context (AGENTS.md, selection, memory) — dropped by the compact, re-added fresh (R-TR-9). */
public final class ContextInput extends TaggedInput {

    public ContextInput(String head, String body) {
        super(Objects.requireNonNull(head, "head"), Objects.requireNonNull(body, "body"));
    }
}
