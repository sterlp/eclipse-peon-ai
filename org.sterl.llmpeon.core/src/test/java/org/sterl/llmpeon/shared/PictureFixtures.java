package org.sterl.llmpeon.shared;

import org.sterl.llmpeon.agent.UserMessageBuilder;
import org.sterl.llmpeon.agent.model.HarnessInput;

import dev.langchain4j.data.message.UserMessage;

/** Test fixture: builds the picture tool's user message. */
public final class PictureFixtures {

    private PictureFixtures() {}

    public static UserMessage of(Picture picture) {
        return new UserMessageBuilder().from(new HarnessInput(picture));
    }
}
