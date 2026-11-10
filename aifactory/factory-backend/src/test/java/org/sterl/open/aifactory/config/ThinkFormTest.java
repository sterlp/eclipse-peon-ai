package org.sterl.open.aifactory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.provider.ThinkSupport;

/**
 * {@link ThinkForm#of} is the one place the factory names a core think form (R-WP-23, UC-WP-67), and the
 * five names are the wire contract. {@code UNKNOWN} needs this file: no core provider answers it today,
 * so no HTTP test could ever reach that branch — and reaching it must still not degrade to "no input".
 */
class ThinkFormTest {

    @Test
    void everyCoreAnswerHasAFormOfItsOwn() {
        assertThat(ThinkForm.of(new ThinkSupport.Toggle())).isEqualTo(ThinkForm.TOGGLE);
        assertThat(ThinkForm.of(new ThinkSupport.Values(List.of("low")))).isEqualTo(ThinkForm.VALUES);
        assertThat(ThinkForm.of(new ThinkSupport.FreeString())).isEqualTo(ThinkForm.FREE);
        assertThat(ThinkForm.of(ThinkSupport.NONE)).isEqualTo(ThinkForm.NONE);
        assertThat(ThinkForm.of(ThinkSupport.UNKNOWN)).isEqualTo(ThinkForm.UNKNOWN);
    }

    @Test
    // UC-WP-67
    void noCoreProviderIsAnsweredWithTheUnknownForm() {
        assertThat(Arrays.stream(AiProvider.values())
                .map(name -> ThinkForm.of(LlmProviders.of(name).thinkSupport())))
                        .doesNotContain(ThinkForm.UNKNOWN);
    }
}
