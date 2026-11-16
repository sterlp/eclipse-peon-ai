package org.sterl.llmpeon.agent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;

/**
 * What the human sends (R-TR-11): the typed text — untagged, so an instruction — then the
 * attachments. Text may be {@code null}/blank (no text part); it is never trimmed or rewritten.
 */
public record UserInput(String text, List<Attachment> attachments) implements Input {

    public UserInput {
        Objects.requireNonNull(attachments, "attachments");
        attachments = List.copyOf(attachments);
    }

    public static UserInput of(String text) {
        return new UserInput(text, List.of());
    }

    @Override
    public List<Content> toContents() {
        var contents = new ArrayList<Content>();
        if (StringUtil.hasValue(text)) contents.add(TextContent.from(text));
        for (var a : attachments) contents.addAll(a.toContents());
        return List.copyOf(contents);
    }
}
