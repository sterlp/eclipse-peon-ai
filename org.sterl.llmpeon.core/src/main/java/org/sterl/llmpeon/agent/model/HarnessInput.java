package org.sterl.llmpeon.agent.model;

import java.util.Objects;

import org.sterl.llmpeon.shared.Picture;

/** A block from the harness itself: queued-message delivery, the picture tool's image (R-TR-10, R-RP-1). */
public final class HarnessInput extends TaggedInput {

    public HarnessInput(String head, String body) {
        super(Objects.requireNonNull(head, "head"), Objects.requireNonNull(body, "body"));
    }

    /** The picture tool's image: picture head, then the image. */
    public HarnessInput(Picture picture) {
        super(Objects.requireNonNull(picture, "picture"), TOOL_PICTURE_BODY);
    }
}
