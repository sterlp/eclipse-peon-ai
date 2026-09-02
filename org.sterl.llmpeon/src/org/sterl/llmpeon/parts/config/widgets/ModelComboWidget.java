package org.sterl.llmpeon.parts.config.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConnectionIdentity;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Model dropdown (editable CCombo) + Refresh button, shared by the basic config page and the
 * per-agent sections of the advanced config page.
 *
 * <p>The combo is filled from the provider's model list, cached per {@link ConnectionIdentity}
 * in {@link ModelListCache}: fetched once when the page opens (or on the refresh button), never
 * while typing. A failed or empty list falls back to the configured model only — no
 * auto-switch. The widget owns the whole fetch/apply lifecycle; the caller only supplies a
 * {@link FetchSnapshot} provider (UI-thread) that reflects the current connection settings.</p>
 */
public class ModelComboWidget extends Composite {

    private final String jobName;
    private final Supplier<FetchSnapshot> snapshotProvider;
    private final CCombo modelCombo;

    /**
     * @param parent           the 2-column grid the widget spans (basic page or agent section)
     * @param jobName          used in the background Job names (e.g. the agent id or "base")
     * @param snapshotProvider UI-thread supplier of the effective connection for the current
     *                         settings — read when a fetch starts and for the stale-guard
     */
    public ModelComboWidget(Composite parent, String jobName, Supplier<FetchSnapshot> snapshotProvider) {
        super(parent, SWT.NONE);
        this.jobName = jobName;
        this.snapshotProvider = snapshotProvider;
        var gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.horizontalSpan = 2;
        setLayoutData(gd);
        setLayout(new GridLayout(3, false));
        var label = new Label(this, SWT.NONE);
        label.setText("Model:");
        label.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        modelCombo = new CCombo(this, SWT.BORDER);
        modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        var refresh = new Button(this, SWT.PUSH);
        refresh.setText("Refresh");
        refresh.setToolTipText("Reload the model list for this connection");
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

    private void applyModelList(List<AiModel> fetched, ConnectionIdentity identity) {
        EclipseUtil.runInUiThread(this, () -> {
            if (!identity.equals(snapshotProvider.get().identity())) return; // settings changed while fetching — stale
            var items = new ArrayList<String>();
            if (fetched != null) items.addAll(fetched.stream().map(AiModel::getId).toList());
            var configured = StringUtil.stripToNull(modelCombo.getText());
            if (configured != null && !items.contains(configured)) items.add(configured);
            modelCombo.setItems(items.toArray(String[]::new));
            if (configured != null) {
                var idx = modelCombo.indexOf(configured);
                if (idx >= 0) modelCombo.select(idx);
                else modelCombo.setText(configured);
            }
        });
    }
}
