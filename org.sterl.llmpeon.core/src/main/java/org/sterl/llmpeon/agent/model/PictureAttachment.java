package org.sterl.llmpeon.agent.model;

import java.util.Objects;

import org.sterl.llmpeon.shared.Picture;

/** A picture the user attached: head {@code picture: <path>}, then the image (R-TR-10/12). */
public final class PictureAttachment extends TaggedInput implements Attachment {

    public PictureAttachment(Picture picture) {
        super(Objects.requireNonNull(picture, "picture"), USER_PICTURE_BODY);
    }
}
