package org.sterl.llmpeon.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * R-CC-12: the result line carries BOTH numbers — the estimate and the last provider-reported
 * request value side by side; without a provider value it says n/a honestly.
 */
class CompactResultTest {

    @Test
    void resultLineShowsEstimateAndRequestTokensSideBySide() {
        // GIVEN — the memory's last model input was 80211 tokens, reported by the provider
        var stats = new CompactResult.Stats(61, 114_000, 40_000, CompactResult.Stage.TOOL_RESULTS,
                500_000, 2_300, "compact-model", 1234L, 80_211, false);

        // WHEN
        var line = CompactResult.compacted(stats).resultLine();

        // THEN — estimate AND provider request value side by side, fixed field names
        assertThat(line).isEqualTo("compressed 61 messages ~114k → input ~40k, result 2k, "
                + "stage: tool results 6000, request 80211 (provider)");
    }

    @Test
    void resultLineRequestNaWithoutProviderValue() {
        // GIVEN — the API never reported an input count
        var stats = new CompactResult.Stats(61, 114_000, 40_000, CompactResult.Stage.TOOL_RESULTS,
                500_000, 2_300, "compact-model", 1234L, null, true);

        // WHEN
        var line = CompactResult.compacted(stats).resultLine();

        // THEN — honestly n/a, and the flag marks the missing provider value
        assertThat(line).isEqualTo("compressed 61 messages ~114k → input ~40k, result 2k, "
                + "stage: tool results 6000, request n/a (no provider value)");
        assertThat(stats.requestIsEstimate()).isTrue();
    }
}
