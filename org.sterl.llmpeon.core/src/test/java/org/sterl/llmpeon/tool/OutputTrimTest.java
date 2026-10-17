package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.OutputTrim;
import org.sterl.llmpeon.shared.OutputTrim.Result;

class OutputTrimTest {

    // BDD-2 (docs/output-trim-service.md)
    // 100 lines x 80 chars (joined 8099 <= 10000) -> bit-identical, no note.
    // Mutation: any drop or cap -> text differs or note appears -> red.
    @Test
    void underCapUnchanged() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 100; i++) lines.add(pad("line " + i, 80));

        Result r = OutputTrim.trim(lines, OutputTrim.DEFAULT_MAX_CHARS);

        assertThat(r.text()).isEqualTo(String.join("\n", lines));
        assertThat(r.truncated()).isFalse();
        assertThat(r.text()).doesNotContain("truncated:");
        assertThat(r.shownLines()).isEqualTo(100);
        assertThat(r.totalLines()).isEqualTo(100);
    }

    // Exact boundary: total == maxChars -> everything in the window, no note.
    // Mutation off-by-one (budget-1) -> one line evicted -> note -> red.
    @Test
    void totalEqualsMaxCharsNoNote() {
        // costs 111+111+111 (head=333) then 222+222+223 (tail=667) = 1000 == maxChars
        List<String> lines = List.of(
                pad("A", 110), pad("B", 110), pad("C", 110),
                pad("D", 221), pad("E", 221), pad("F", 222));

        Result r = OutputTrim.trim(lines, 1000);

        assertThat(r.totalChars()).isEqualTo(1000);
        assertThat(r.truncated()).isFalse();
        assertThat(r.text()).doesNotContain("truncated:");
        assertThat(r.text()).isEqualTo(String.join("\n", lines));
        assertThat(r.shownLines()).isEqualTo(6);
    }

    // BDD-3 (docs/output-trim-service.md)
    // 20 lines x 99 chars (total 2000), maxChars=500 -> head=1 + tail=3 = 4 shown, line 18 kept.
    // Mutation: tail-only (head dropped) -> line 1 missing; split flip 3/1 -> tail keeps 4+ -> red.
    @Test
    void headTailExcerpt() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 20; i++) lines.add(pad("line " + i, 99));

        Result r = OutputTrim.trim(lines, 500);

        assertThat(r.shownLines()).isEqualTo(4);
        assertThat(r.truncated()).isTrue();
        assertThat(r.text()).startsWith("line 1");
        assertThat(r.text()).contains("line 18");
        assertThat(r.text()).contains("line 20");
        assertThat(r.text()).doesNotContain("line 10");
        assertThat(r.text()).contains("truncated: 2000 chars total, 399 shown");
    }

    // BDD-6 (docs/output-trim-service.md)
    // 500_000 'a' -> line = 'a'x500 + …(+499500 chars), note at end, totalChars=500001, shownChars=516.
    // Mutation: cap at 2000 (old R-OD-6) -> marker and shownChars differ -> red.
    @Test
    void minifiedLineCapped() {
        Result r = OutputTrim.trim(List.of("a".repeat(500_000)), OutputTrim.DEFAULT_MAX_CHARS);

        assertThat(r.text()).isEqualTo(
                "a".repeat(500) + "…(+499500 chars)"
                        + "\n… (truncated: 500001 chars total, 516 shown)");
        assertThat(r.truncatedLines()).isEqualTo(1);
        assertThat(r.truncated()).isTrue();
        assertThat(r.totalChars()).isEqualTo(500_001);
        assertThat(r.shownChars()).isEqualTo(516);
        assertThat(r.shownLines()).isEqualTo(1);
    }

    // BDD-1 component twin (docs/output-trim-service.md)
    // 3M-char line -> bounded result, totalChars=3000001, first 500 'a' kept.
    // Mutation: no per-line cap -> text ~3M chars -> red.
    @Test
    void threeMillionLineBounded() {
        Result r = OutputTrim.trim(List.of("a".repeat(3_000_000)), OutputTrim.DEFAULT_MAX_CHARS);

        assertThat(r.totalChars()).isEqualTo(3_000_001);
        assertThat(r.shownChars()).isEqualTo(517);
        assertThat(r.truncatedLines()).isEqualTo(1);
        assertThat(r.truncated()).isTrue();
        assertThat(r.text()).startsWith("a".repeat(500));
        assertThat(r.text()).contains("…(+2999500 chars)");
        assertThat(r.text()).contains("truncated: 3000001 chars total, 517 shown");
        assertThat(r.text().length()).isLessThanOrEqualTo(10_100);
    }

    // Empty input -> empty result, no note; streaming twin reports isEmpty.
    @Test
    void emptyInput() {
        Result r = OutputTrim.trim(List.of(), OutputTrim.DEFAULT_MAX_CHARS);

        assertThat(r.text()).isEmpty();
        assertThat(r.shownLines()).isZero();
        assertThat(r.totalLines()).isZero();
        assertThat(r.totalChars()).isZero();
        assertThat(r.shownChars()).isZero();
        assertThat(r.truncated()).isFalse();

        OutputTrim.Window window = OutputTrim.window(OutputTrim.DEFAULT_MAX_CHARS);
        assertThat(window.isEmpty()).isTrue();
        assertThat(window.build()).isEqualTo(r);
    }

    // Contract pin ("one behaviour, one implementation"): the one-shot Grep path and the
    // streaming Shell path must produce identical Results for identical input.
    // Mutation: a separately re-implemented trim() diverging from the window -> red.
    @Test
    void batchTrimEqualsStreaming() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 60; i++) lines.add(pad("line " + i, i % 7 == 0 ? 600 : 40));

        Result batch = OutputTrim.trim(lines, 2000);

        OutputTrim.Window window = OutputTrim.window(2000);
        for (String line : lines) window.addLine(line);
        Result streaming = window.build();

        assertThat(batch).isEqualTo(streaming);
        assertThat(batch.truncated()).isTrue();
        assertThat(batch.truncatedLines()).isGreaterThan(0);
    }

    // Degenerate tiny budget: maxChars=100, 50 lines x 50 chars -> bounded (2 shown) + note.
    // Mutation: unbounded window -> all 50 lines shown, no note -> red.
    @Test
    void degenerateTinyBudget() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 50; i++) lines.add(pad("line " + i, 50));

        Result r = OutputTrim.trim(lines, 100);

        assertThat(r.truncated()).isTrue();
        assertThat(r.totalLines()).isEqualTo(50);
        assertThat(r.shownLines()).isEqualTo(2);
        assertThat(r.text()).startsWith("line 1");
        assertThat(r.text()).contains("line 50");
        assertThat(r.text()).doesNotContain("line 25");
        assertThat(r.text()).contains("truncated:");
    }

    private static String pad(String tag, int length) {
        return tag + "y".repeat(length - tag.length());
    }
}
