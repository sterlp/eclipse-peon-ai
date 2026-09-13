package org.sterl.llmpeon.parts.config.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConnectionIdentity;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Model dropdown (native editable {@link Combo}) + Refresh button, shared by the basic config
 * page and the per-agent sections of the advanced config page.
 *
 * <p>The combo is filled from the provider's model list, cached per {@link ConnectionIdentity}
 * in {@link ModelListCache}: fetched once when the page opens (or on the refresh button), never
 * while typing. A failed or empty list falls back to the configured model only — no
 * auto-switch. The widget owns the whole fetch/apply lifecycle; the caller only supplies a
 * {@link FetchSnapshot} provider (UI-thread) that reflects the current connection settings.</p>
 *
 * <p>This is a plain controller (no SWT parent of its own): it creates the combo and the
 * refresh button directly in the given 2-column parent grid, so the combo sits in the same
 * field column as the sibling fields. <b>Constructor contract:</b> the caller creates the
 * "Model:" label in the parent grid <b>before</b> constructing this widget — otherwise the
 * combo lands in the label column.</p>
 */
public class ModelComboWidget {

    private final String jobName;
    private final Supplier<FetchSnapshot> snapshotProvider;
    private final Combo modelCombo;

    /**
     * @param parent           the 2-column grid to build into (basic page or agent section);
     *                         the caller's "Model:" label must already occupy its label cell
     * @param jobName          used in the background Job names (e.g. the agent id or "base")
     * @param snapshotProvider UI-thread supplier of the effective connection for the current
     *                         settings — read when a fetch starts and for the stale-guard
     */
    public ModelComboWidget(Composite parent, String jobName, Supplier<FetchSnapshot> snapshotProvider) {
        this.jobName = jobName;
        this.snapshotProvider = snapshotProvider;
        modelCombo = new Combo(parent, SWT.BORDER);
        modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        var refresh = new Button(parent, SWT.PUSH);
        refresh.setText("Refresh");
        refresh.setToolTipText("Reload the model list for this connection");
        var refreshGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        refreshGd.horizontalSpan = 2;
        refresh.setLayoutData(refreshGd);
        refresh.addListener(SWT.Selection, e -> refreshModels());
    }

    /** Sets the configured model (null shows as empty). */
    public void setModel(String model) {
        modelCombo.setText(StringUtil.stripToEmpty(model));
    }

    /** The currently shown model (may be the user's free text). */
    public String getModel() {
        return modelCombo.getText();
    }

    // --- model list (per ConnectionIdentity, cached on success only) ---

    /** Fetches the model list for the current settings (page open). Cached per identity. */
    public void fetchModels() {
        var snapshot = snapshotProvider.get(); // UI thread: capture before the background Job
        Job.create("Loading models (" + jobName + ")", monitor -> {
            var list = ModelListCache.instance().getOrFetch(snapshot.identity(), fetchList(snapshot));
            applyModelList(list, snapshot.identity());
            return Status.OK_STATUS;
        }).schedule();
    }

    /** Manual refresh: always refetches; a failed fetch keeps the previous list. */
    private void refreshModels() {
        var snapshot = snapshotProvider.get(); // UI thread: capture before the background Job
        Job.create("Refreshing models (" + jobName + ")", monitor -> {
            var cache = ModelListCache.instance();
            var list = cache.refresh(snapshot.identity(), fetchList(snapshot));
            applyModelList(list != null ? list : cache.cached(snapshot.identity()), snapshot.identity());
            return Status.OK_STATUS;
        }).schedule();
    }

    /**
     * UI-thread snapshot of the effective connection for the current settings, captured before
     * the background fetch Job starts. The Job body reads only this snapshot — never the
     * widgets (SWT widgets are not thread-safe; reading them off the UI thread throws
     * {@code SWTException: Invalid thread access}).
     */
    public record FetchSnapshot(ConnectionIdentity identity, LlmConfig buildConfig) {}

    /** SWT-free: the fetcher for a captured snapshot — safe to run in a background Job. */
    public static Supplier<List<AiModel>> fetchList(FetchSnapshot snapshot) {
        return () -> LlmProviders.of(snapshot.identity().provider()).listAiModels(snapshot.buildConfig());
    }

    /** SWT-free base snapshot for the basic page (no per-agent overrides). */
    public static FetchSnapshot baseSnapshot(LlmConfig base) {
        var effective = base.effectiveConnectionFor(AgentModelConfig.empty());
        return new FetchSnapshot(effective.identity(), effective.buildConfig());
    }

    /**
     * Applies the fetched model list to the combo. Dedup rule (R-ML4): the typed input is
     * appended only if no entry equals it case-insensitively; on a match the server's ID wins
     * (canonical) and is selected; without a server list the input stays verbatim.
     */
    private void applyModelList(List<AiModel> fetched, ConnectionIdentity identity) {
        EclipseUtil.runInUiThread(modelCombo, () -> {
            if (!identity.equals(snapshotProvider.get().identity())) return; // settings changed while fetching — stale
            var items = new ArrayList<String>();
            if (fetched != null) items.addAll(fetched.stream().map(AiModel::getId).toList());
            var configured = StringUtil.stripToNull(modelCombo.getText());
            var idx = indexOfIgnoreCase(items, configured);
            if (configured != null && idx < 0) {
                items.add(configured);
                idx = items.size() - 1;
            }
            modelCombo.setItems(items.toArray(String[]::new));
            if (idx >= 0) modelCombo.select(idx);
        });
    }

    /** First position whose entry equals value case-insensitively; -1 for null value or no match. */
    private static int indexOfIgnoreCase(List<String> items, String value) {
        if (value == null) return -1;
        for (var i = 0; i < items.size(); i++) {
            if (value.equalsIgnoreCase(items.get(i))) return i;
        }
        return -1;
    }
}
