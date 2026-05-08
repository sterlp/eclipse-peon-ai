package org.sterl.llmpeon.shared;

import java.time.Instant;

public record OnPartialAiResponse(Type type, String value, Instant startedAt) {

    public enum Type { WAITING, THINK, ANSWER, TOOL }
}
