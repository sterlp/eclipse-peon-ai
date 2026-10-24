package org.sterl.llmpeon.tool.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * R-LSR-1…6 (org.sterl.llmpeon.core/docs/search-agent-tool.md § Rolling Search Context): the rolling
 * hot block on its own — pair format, the raw 10.000-char tail cut (ADR-0075), the disclosure line
 * only after a real cut, and no lost update across threads. Wiring it into the sub-loop is I-2.
 */
class RollingSearchContextTest {

    /** The 5-digit blocks 00000.. — a cut position then reads as the surviving block number. */
    private static String digits(int blocks) {
        var all = new StringBuilder();
        for (int i = 0; i < blocks; i++) all.append(String.format("%05d", i));
        return all.toString();
    }

    /** The pair content of a rendered block — everything the cap counts, decoration cut away. */
    private static String pairContent(String block, boolean cut) {
        var head = RollingSearchContext.START + "\n";
        var tail = (cut ? RollingSearchContext.DISCLOSURE + "\n" : "") + RollingSearchContext.END;
        return block.substring(head.length(), block.length() - tail.length());
    }

    // R-LSR-5
    @Test
    @Timeout(10)
    void givenEmptyContext_whenRendering_thenNothingIsInjected() {
        // GIVEN — a view that has not searched yet
        var ctx = new RollingSearchContext();

        // THEN — no block, so the sub-loop's static messages stay exactly as they are today
        assertThat(ctx.render()).isEmpty();
        assertThat(ctx.isEmpty()).isTrue();
    }

    // R-LSR-1
    @Test
    @Timeout(10)
    void givenCompletePair_whenAppending_thenBlockCarriesPromptAndAnswer() {
        // GIVEN — an empty block
        var ctx = new RollingSearchContext();

        // WHEN — two complete searches append their pairs
        ctx.append("q1", "a1");
        ctx.append("q2", "a2");

        // THEN — both pairs, in append order, framed by the block headers
        var block = ctx.render();
        assertThat(block).startsWith(RollingSearchContext.START + "\n")
                .endsWith("\n" + RollingSearchContext.END);
        assertThat(block).contains("Frage: q1\nAntwort: a1\n", "Frage: q2\nAntwort: a2\n");
        assertThat(block.indexOf("Frage: q1")).isLessThan(block.indexOf("Frage: q2"));
    }

    // R-LSR-2
    @Test
    @Timeout(10)
    void givenBlankAnswer_whenAppending_thenBlockStaysEmpty() {
        // GIVEN — a search that produced no answer text
        var ctx = new RollingSearchContext();

        // WHEN
        ctx.append("q", "   ");

        // THEN — nothing landed, the block still injects nothing
        assertThat(ctx.isEmpty()).isTrue();
        assertThat(ctx.render()).isEmpty();
    }

    // R-LSR-3
    // UC-LSR-3
    @Test
    @Timeout(10)
    void blockTrimmedToLastTenThousandChars() {
        // GIVEN — one answer of 14.000 digits, so the raw cut lands inside the answer
        var ctx = new RollingSearchContext();

        // WHEN
        ctx.append("q", digits(2_800));

        // THEN — exactly the last 10.000 pair chars survive: header and disclosure are decoration
        //        added after the cut, so they cannot shift the limit (R-LSR-3, ADR-0075)
        var block = ctx.render();
        var content = pairContent(block, true);
        assertThat(content).hasSize(RollingSearchContext.MAX_CHARS);
        assertThat(content.strip()).matches("\\d+");
        assertThat(content).contains("02799").doesNotContain("00000").doesNotContain("00799");
    }

    // R-LSR-4
    // UC-LSR-4
    @Test
    @Timeout(10)
    void disclosureOnlyWhenTrimmed() {
        // GIVEN — a pair over the cap
        var cut = new RollingSearchContext();
        cut.append("q", digits(2_800));

        // THEN — the block names its own cut (a truncation the search model cannot see is a lie)
        assertThat(cut.render()).contains("(older context dropped to fit cap)");

        // GIVEN — a pair content of exactly MAX_CHARS: "Frage: Q\nAntwort: " plus the answer plus the
        //         pair's own trailing newline, so nothing has to be dropped
        var exact = new RollingSearchContext();
        int pairOverhead = "Frage: Q\nAntwort: \n".length();
        exact.append("Q", "x".repeat(RollingSearchContext.MAX_CHARS - pairOverhead));

        // THEN — at the cap the disclosure line must not appear
        assertThat(exact.render()).doesNotContain("(older context dropped to fit cap)");
        assertThat(pairContent(exact.render(), false)).hasSize(RollingSearchContext.MAX_CHARS);
    }

    // R-LSR-6
    @Test
    @Timeout(10)
    void givenConcurrentAppends_whenRendering_thenNoPairIsLost() throws InterruptedException {
        // GIVEN — 4 threads × 25 distinct pairs (far below the cap), released by one start gate
        var ctx = new RollingSearchContext();
        int threads = 4;
        int perThread = 25;
        var start = new CountDownLatch(threads);
        var workers = new ArrayList<Thread>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            var worker = new Thread(() -> {
                start.countDown();
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        ctx.append("t-" + id + "-" + i, "a-" + id + "-" + i);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            workers.add(worker);
            worker.start();
        }

        // WHEN — every thread appended all of its pairs
        for (var worker : workers) worker.join(5_000);

        // THEN — all 100 pairs are there, each one intact (no lost update, no torn pair)
        var block = ctx.render();
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                assertThat(block).contains("Frage: t-" + t + "-" + i + "\nAntwort: a-" + t + "-" + i + "\n");
            }
        }
    }
}
