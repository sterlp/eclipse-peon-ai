package org.sterl.open.aifactory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The context smoke test — on the throwaway home of {@link FactorySpringTest}
 * ({@code target/test-home}), so no build ever reads or writes the developer's {@code ~/.aifactory}.
 */
class AifactoryApplicationTests extends FactorySpringTest {

    @Autowired
    FactoryHome home;

    /** Pinned, not assumed: without the dynamic property this stays green while writing to ~/.aifactory. */
    @Test
    void contextLoadsWithAThrowawayFactoryHome() {
        assertThat(home.root()).isEqualTo(HOME);
    }
}
