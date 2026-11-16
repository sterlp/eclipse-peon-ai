package org.sterl.llmpeon.agent.model;

/** What the user attaches to a message (R-TR-11): a file's text or a picture — never context or harness blocks. */
public sealed interface Attachment extends Input permits TextAttachment, PictureAttachment {
}
