package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;

import dev.langchain4j.data.message.UserMessage;

class PictureFileTest extends AbstractMemoryFileTest {

    private static final int MB4 = 4 * 1024 * 1024;

    // UC-RP-3
    @Test
    void exactly4MbIsLoadedAndOnePastItIsRejectedWithTheRealSize() throws IOException {
        // GIVEN
        var ok = Files.write(tmp.resolve("exact.PNG"), new byte[MB4]);
        var big = Files.write(tmp.resolve("big.png"), new byte[MB4 + 1]);

        // WHEN + THEN — the cap is "> 4 MB", the extension is case-insensitive
        var loaded = PictureFile.load(ok);
        assertThat(loaded.mime()).isEqualTo("image/png");
        assertThat(loaded.sizeBytes()).isEqualTo(MB4);
        assertThat(Base64.getDecoder().decode(loaded.base64())).hasSize(MB4);
        assertThatThrownBy(() -> PictureFile.load(big))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("picture too large: 4.0 MB, max 4 MB — ");
    }

    // UC-RP-3
    @Test
    void sevenMbNamesTheRealSize() throws IOException {
        var seven = Files.write(tmp.resolve("seven.jpg"), new byte[7 * 1024 * 1024]);

        assertThatThrownBy(() -> PictureFile.load(seven))
                .hasMessageStartingWith("picture too large: 7.0 MB, max 4 MB — ");
    }

    // UC-RP-3
    @Test
    void unsupportedTypeAndMissingFileAreIllegalArguments() throws IOException {
        var txt = Files.writeString(tmp.resolve("notes.txt"), "hello");

        assertThatThrownBy(() -> PictureFile.load(txt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("unsupported picture type: .txt (png, jpg, jpeg, gif, webp) — ");
        assertThatThrownBy(() -> PictureFile.load(tmp.resolve("missing.png")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File not found")
                .hasMessageContaining("missing.png");
    }

    // UC-RP-8
    @Test
    void theDisplayPathIsTheOnlyPathInResultHeadPlaceholderAndErrors() throws IOException {
        // GIVEN — a real file under a clearly different disk path
        var dir = Files.createDirectories(tmp.resolve("unique-7f3a/real"));
        var disk = Files.write(dir.resolve("real-disk-location.png"), new byte[16]);
        var diskPath = disk.toAbsolutePath().normalize().toString();

        // WHEN
        var picture = PictureFile.load(disk, "/proj/a.png");
        var message = PictureMessage.of(picture);

        // THEN
        assertThat(picture.resultText()).contains("/proj/a.png").doesNotContain(diskPath);
        assertThat(PictureMessage.pathOf(message)).isEqualTo("/proj/a.png");
        var placeholder = ChatMessageUtil.toString((UserMessage) message);
        assertThat(placeholder).contains("[picture removed for compact: /proj/a.png]").doesNotContain("unique-7f3a");
    }

    // UC-RP-8
    @Test
    void tooLargeNamesTheDisplayPathNotTheDiskPath() throws IOException {
        var dir = Files.createDirectories(tmp.resolve("unique-9c1d/real"));
        var big = Files.write(dir.resolve("real-disk-location.png"), new byte[MB4 + 1]);

        assertThatThrownBy(() -> PictureFile.load(big, "/proj/big.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("picture too large: ")
                .hasMessageContaining("/proj/big.png")
                .message().doesNotContain("unique-9c1d");
    }

    // UC-RP-8
    @Test
    void missingFileNamesTheDisplayPathNotTheDiskPath() {
        Path disk = tmp.resolve("unique-2b8e/real-disk-location.png");

        assertThatThrownBy(() -> PictureFile.load(disk, "/proj/missing.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File not found")
                .hasMessageContaining("/proj/missing.png")
                .message().doesNotContain("unique-2b8e");
    }
}
