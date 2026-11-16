package org.sterl.llmpeon.agent.model;

import java.util.Objects;

/** A file the user attached: head {@code file: <name>}, the content is data, not instruction (R-TR-12). */
public final class TextAttachment extends TaggedInput implements Attachment {

    public TextAttachment(String name, String body) {
        super(FILE_HEAD + Objects.requireNonNull(name, "name"), Objects.requireNonNull(body, "body"));
    }
}
