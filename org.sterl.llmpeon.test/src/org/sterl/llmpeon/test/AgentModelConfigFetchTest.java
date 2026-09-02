package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget;

/**
 * SWT thread fix for the model-list fetch (shared {@link ModelComboWidget}): the fetch works off
 * a snapshot captured on the UI thread, and the fetcher itself is SWT-free (no widget reads) —
 * so the background Job body can no longer throw {@code SWTException: Invalid thread access}.
 */
public class AgentModelConfigFetchTest extends AbstractUnitTest {

    @Test
    public void fetchListUsesCapturedSnapshotWithoutWidgets() {
        // GIVEN a snapshot captured for the base connection (no widgets involved)
        var base = mockLlmServer.newConfig("gpt-4o");
        var effective = base.effectiveConnectionFor(AgentModelConfig.empty());
        var snapshot = new ModelComboWidget.FetchSnapshot(
                effective.identity(), effective.buildConfig());

        // WHEN the SWT-free fetcher is invoked (as the background Job would)
        var list = ModelComboWidget.fetchList(snapshot).get();

        // THEN the server's model ids come back — no Display/widgets were needed
        assertNotNull(list);
        assertEquals(List.of("gpt-4o", "mock-model"), ids(list));
    }

    private static List<String> ids(List<AiModel> models) {
        return models.stream().map(AiModel::getId).toList();
    }
}
