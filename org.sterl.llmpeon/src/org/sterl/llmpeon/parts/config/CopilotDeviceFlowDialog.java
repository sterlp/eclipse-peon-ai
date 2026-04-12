package org.sterl.llmpeon.parts.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.sterl.llmpeon.parts.PeonConstants;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CopilotDeviceFlowDialog extends Dialog {

    private static final String CLIENT_ID              = "01ab8ac9400c4e429b23";
    private static final String DEFAULT_GITHUB_DOMAIN  = "github.com";
    private static final String POLL_URL               = "https://github.com/login/oauth/access_token";

    private final HttpClient http     = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String deviceCode;
    private String verificationUri;
    private int    intervalSeconds = 5;
    private String enterpriseDomain; // null means standard github.com

    private Label  statusLabel;
    private Text   userCodeText;
    private Text   enterpriseUrlText;
    private Button copyButton;
    private Link   browserLink;
    private Button okButton;
    private Font   codeFont;
    private Job    pollingJob;

    public CopilotDeviceFlowDialog(Shell parentShell) {
        super(parentShell);
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Login with GitHub Copilot");
        shell.setSize(500, 340);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));

        // Static instructions
        Label instructions = new Label(area, SWT.WRAP);
        instructions.setText(
                "To authorize Eclipse Peon with GitHub Copilot:\n" +
                "  1. Click the link below to open GitHub in your browser.\n" +
                "  2. Enter the code shown — copy it first to save typing.\n" +
                "  3. Authorize the app on GitHub.\n" +
                "  4. Once authorized, click Done.");
        instructions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Optional: GitHub Enterprise Server domain
        Composite enterpriseRow = new Composite(area, SWT.NONE);
        enterpriseRow.setLayout(new GridLayout(2, false));
        enterpriseRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Label enterpriseLabel = new Label(enterpriseRow, SWT.NONE);
        enterpriseLabel.setText("GitHub Enterprise URL (optional):");
        enterpriseUrlText = new Text(enterpriseRow, SWT.BORDER);
        enterpriseUrlText.setMessage("company.ghe.com");
        enterpriseUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(area, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Code row: large text + Copy button side by side
        Composite codeRow = new Composite(area, SWT.NONE);
        codeRow.setLayout(new GridLayout(2, false));
        codeRow.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        userCodeText = new Text(codeRow, SWT.BORDER | SWT.READ_ONLY | SWT.CENTER);
        FontData[] fd = userCodeText.getFont().getFontData();
        fd[0].setHeight(18);
        fd[0].setStyle(SWT.BOLD);
        codeFont = new Font(area.getDisplay(), fd);
        userCodeText.setFont(codeFont);
        GridData codeGd = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        codeGd.widthHint = 200;
        userCodeText.setLayoutData(codeGd);
        userCodeText.setVisible(false);

        copyButton = new Button(codeRow, SWT.PUSH);
        copyButton.setText("Copy");
        copyButton.setVisible(false);
        copyButton.addListener(SWT.Selection, e -> {
            Clipboard clip = new Clipboard(codeRow.getDisplay());
            clip.setContents(
                    new Object[]{ userCodeText.getText() },
                    new Transfer[]{ TextTransfer.getInstance() });
            clip.dispose();
        });

        browserLink = new Link(area, SWT.WRAP);
        browserLink.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        browserLink.setVisible(false);
        browserLink.addSelectionListener(
                SelectionListener.widgetSelectedAdapter(e -> Program.launch(verificationUri)));

        new Label(area, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLabel = new Label(area, SWT.WRAP);
        statusLabel.setText("Starting device flow...");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        okButton = createButton(parent, IDialogConstants.OK_ID, "Done", false);
        okButton.setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, true);
    }

    @Override
    public void create() {
        super.create();
        startDeviceFlow();
    }

    @Override
    protected void cancelPressed() {
        if (pollingJob != null) pollingJob.cancel();
        super.cancelPressed();
    }

    @Override
    public boolean close() {
        if (codeFont != null && !codeFont.isDisposed()) codeFont.dispose();
        return super.close();
    }

    private void startDeviceFlow() {
        // Read enterprise domain from UI before leaving the UI thread (called on UI thread)
        String entered = enterpriseUrlText.getText().trim();
        enterpriseDomain = entered.isEmpty() ? null
                : entered.replaceAll("^https?://", "").replaceAll("/+$", "");
        pollingJob = Job.create("GitHub Copilot login", (IProgressMonitor monitor) -> {
            try {
                String domain = enterpriseDomain != null ? enterpriseDomain : DEFAULT_GITHUB_DOMAIN;
                String deviceCodeUrl = "https://" + domain + "/login/device/code";
                String body = "client_id=" + CLIENT_ID + "&scope=read:user";
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(deviceCodeUrl))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    setStatus("Error requesting device code: HTTP " + response.statusCode());
                    return new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID,
                            "Device code request failed: " + response.body());
                }

                var json = mapper.readTree(response.body());
                deviceCode      = json.get("device_code").asText();
                String userCode = json.get("user_code").asText();
                verificationUri = json.get("verification_uri").asText();
                intervalSeconds = json.has("interval") ? json.get("interval").asInt() : 5;

                asyncExec(() -> {
                    if (isDisposed()) return;
                    userCodeText.setText(userCode);
                    userCodeText.setVisible(true);
                    copyButton.setVisible(true);
                    browserLink.setText("<a>" + verificationUri + "</a>");
                    browserLink.setVisible(true);
                    setStatus("Waiting for you to authorize in the browser...");
                    getShell().layout(true, true);
                });

                pollForToken(monitor);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                setStatus("Error: " + e.getMessage());
                return new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, e.getMessage(), e);
            }
            return Status.OK_STATUS;
        });
        pollingJob.schedule();
    }

    private void pollForToken(IProgressMonitor monitor) throws Exception {
        while (!monitor.isCanceled()) {
            Thread.sleep(intervalSeconds * 1000L);
            if (isDisposed()) return;

            String pollBody = "client_id=" + CLIENT_ID
                    + "&device_code=" + deviceCode
                    + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(POLL_URL))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(pollBody))
                    .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var json = mapper.readTree(response.body());

            if (json.has("access_token")) {
                String oauthToken = json.get("access_token").asText();
                LlmPreferenceInitializer.saveGitHubOAuthToken(oauthToken, enterpriseDomain);
                asyncExec(() -> {
                    if (isDisposed()) return;
                    setStatus("Successfully authenticated! Click Done to finish.");
                    okButton.setEnabled(true);
                    okButton.setFocus();
                });
                return;
            }

            String error = json.has("error") ? json.get("error").asText() : "";
            switch (error) {
                case "authorization_pending":
                    break;
                case "slow_down":
                    intervalSeconds += 5;
                    break;
                case "expired_token":
                    setStatus("Authorization code expired. Please close and try again.");
                    return;
                default:
                    setStatus("Error: " + (json.has("error_description")
                            ? json.get("error_description").asText() : error));
                    return;
            }
        }
    }

    private boolean isDisposed() {
        return getShell() == null || getShell().isDisposed();
    }

    private void setStatus(String text) {
        asyncExec(() -> { if (!isDisposed()) statusLabel.setText(text); });
    }

    private void asyncExec(Runnable r) {
        if (isDisposed()) return;
        getShell().getDisplay().asyncExec(r);
    }
}
