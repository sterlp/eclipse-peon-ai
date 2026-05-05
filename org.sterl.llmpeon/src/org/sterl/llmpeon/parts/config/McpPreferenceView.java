package org.sterl.llmpeon.parts.config;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sterl.llmpeon.mcp.McpServerConfig;
import org.sterl.llmpeon.mcp.McpServerConfig.McpTransportType;
import org.sterl.llmpeon.mcp.McpService;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.shared.StringUtil;


public class McpPreferenceView extends PreferencePage implements IWorkbenchPreferencePage {

    private Table table;
    private Button btnAdd;
    private Button btnEdit;
    private Button btnRemove;
    private Button btnTest;

    private final List<McpServerConfig> servers = new ArrayList<>();

    public McpPreferenceView() {
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PeonConstants.PLUGIN_ID));
        setDescription("Configure MCP (Model Context Protocol) servers. All listed servers are used when MCP is enabled.");
    }

    @Override
    protected Control createContents(Composite parent) {
        servers.clear();
        servers.addAll(McpPreferenceInitializer.loadServers());

        Composite container = new Composite(parent, SWT.NONE);
        var layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        container.setLayout(layout);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        table = new Table(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        var tableGd = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableGd.verticalSpan = 5;
        table.setLayoutData(tableGd);

        var colName = new TableColumn(table, SWT.NONE);
        colName.setText("Name");
        colName.setWidth(120);
        var colType = new TableColumn(table, SWT.NONE);
        colType.setText("Type");
        colType.setWidth(60);
        var colUrl = new TableColumn(table, SWT.NONE);
        colUrl.setText("URL / Command");
        colUrl.setWidth(250);
        var colProtocol = new TableColumn(table, SWT.NONE);
        colProtocol.setText("Protocol");
        colProtocol.setWidth(90);
        var colKey = new TableColumn(table, SWT.NONE);
        colKey.setText("API Key / Env");
        colKey.setWidth(100);

        table.addListener(SWT.Selection, e -> updateButtonStates());
        table.addListener(SWT.MouseDoubleClick, e -> onEdit());

        btnAdd = new Button(container, SWT.PUSH);
        btnAdd.setText("Add...");
        btnAdd.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        btnAdd.addListener(SWT.Selection, e -> onAdd());

        btnEdit = new Button(container, SWT.PUSH);
        btnEdit.setText("Edit...");
        btnEdit.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        btnEdit.setEnabled(false);
        btnEdit.addListener(SWT.Selection, e -> onEdit());

        btnRemove = new Button(container, SWT.PUSH);
        btnRemove.setText("Remove");
        btnRemove.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        btnRemove.setEnabled(false);
        btnRemove.addListener(SWT.Selection, e -> onRemove());

        btnTest = new Button(container, SWT.PUSH);
        btnTest.setText("Test...");
        btnTest.setToolTipText("Connect to selected server and list its tools");
        btnTest.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        btnTest.setEnabled(false);
        btnTest.addListener(SWT.Selection, e -> onTest());

        refreshTable();
        return container;
    }

    private void refreshTable() {
        table.removeAll();
        for (var s : servers) {
            var item = new TableItem(table, SWT.NONE);
            setItemData(item, s);
        }
    }

    private void setItemData(TableItem item, McpServerConfig s) {
        item.setText(0, s.name());
        item.setText(1, s.type().name());
        if (s.type() == McpTransportType.STDIO) {
            item.setText(2, (s.command() + " " + s.args()).trim());
            item.setText(3, s.protocolVersion());
            item.setText(4, StringUtil.hasNoValue(s.envVars()) ? "" : "<env set>");
        } else {
            item.setText(2, s.url());
            item.setText(3, s.protocolVersion());
            item.setText(4, StringUtil.hasNoValue(s.apiKey()) ? "<no api key>" : "****");
        }
    }

    private void updateButtonStates() {
        boolean selected = table.getSelectionIndex() >= 0;
        btnEdit.setEnabled(selected);
        btnRemove.setEnabled(selected);
        btnTest.setEnabled(selected);
    }

    private void onAdd() {
        var dialog = new McpServerDialog(getShell(), null);
        if (dialog.open() == IDialogConstants.OK_ID) {
            servers.add(dialog.getResult());
            refreshTable();
        }
    }

    private void onEdit() {
        int idx = table.getSelectionIndex();
        if (idx < 0) return;
        var dialog = new McpServerDialog(getShell(), servers.get(idx));
        if (dialog.open() == IDialogConstants.OK_ID) {
            servers.set(idx, dialog.getResult());
            setItemData(table.getItem(idx), servers.get(idx));
        }
    }

    private void onRemove() {
        int idx = table.getSelectionIndex();
        if (idx < 0) return;
        servers.remove(idx);
        table.remove(idx);
        updateButtonStates();
    }

    private void onTest() {
        int idx = table.getSelectionIndex();
        if (idx < 0) return;
        var server = servers.get(idx);
        try {
            var toolNames = McpService.testConnect(server);
            if (toolNames.isEmpty()) {
                setMessage("Connected to '" + server.name() + "' — no tools found", INFORMATION);
            } else {
                setMessage("Connected to '" + server.name() + "' — " + toolNames.size()
                        + " tool(s): " + String.join(", ", toolNames), INFORMATION);
            }
        } catch (Exception e) {
            setErrorMessage("Cannot connect to '" + server.name() + "': " + e.getMessage());
        }
    }

    @Override
    public boolean performOk() {
        McpPreferenceInitializer.saveServers(servers);
        return true;
    }

    @Override
    protected void performDefaults() {
        servers.clear();
        refreshTable();
    }

    @Override
    public void init(IWorkbench workbench) {}

    // -------------------------------------------------------------------------
    // Inner dialog for add/edit
    // -------------------------------------------------------------------------

    static class McpServerDialog extends TitleAreaDialog {

        private Combo cmbType;
        private Text txtName;
        private Text txtProtocol;

        // HTTP fields
        private Group grpHttp;
        private Text txtUrl;
        private Text txtApiKey;

        // STDIO fields
        private Group grpStdio;
        private Text txtCommand;
        private Text txtArgs;
        private Text txtEnvVars;

        private final McpServerConfig initial;
        private McpServerConfig result;

        McpServerDialog(Shell parent, McpServerConfig initial) {
            super(parent);
            this.initial = initial;
        }

        @Override
        public void create() {
            super.create();
            setTitle(initial == null ? "Add MCP Server" : "Edit MCP Server");
            setMessage("Configure connection details for the MCP server.");
            getShell().setMinimumSize(540, 420);
            getShell().setSize(540, 420);
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            var area = (Composite) super.createDialogArea(parent);
            var container = new Composite(area, SWT.NONE);
            container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            container.setLayout(new GridLayout(2, false));

            // Type selector
            addLabel(container, "Type:");
            cmbType = new Combo(container, SWT.READ_ONLY | SWT.DROP_DOWN);
            cmbType.setItems("HTTP (SSE)", "STDIO (local process)");
            cmbType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            boolean isStdio = initial != null && initial.type() == McpServerConfig.McpTransportType.STDIO;
            cmbType.select(isStdio ? 1 : 0);

            // Common fields
            addLabel(container, "Name:");
            txtName = addText(container, initial != null ? initial.name() : "my-mcp");

            addLabel(container, "Protocol Version:");
            txtProtocol = addText(container, initial != null ? initial.protocolVersion() : McpServerConfig.DEFAULT_PROTOCOL_VERSION);

            // HTTP group
            grpHttp = new Group(container, SWT.NONE);
            grpHttp.setText("HTTP / SSE Settings");
            grpHttp.setLayout(new GridLayout(2, false));
            var grpHttpGd = new GridData(SWT.FILL, SWT.FILL, true, false);
            grpHttpGd.horizontalSpan = 2;
            grpHttp.setLayoutData(grpHttpGd);

            addLabel(grpHttp, "URL (SSE endpoint):");
            txtUrl = addText(grpHttp, initial != null ? initial.url() : "https://mcp.context7.com/mcp");

            addLabel(grpHttp, "API Key (optional):");
            txtApiKey = addText(grpHttp, initial != null ? initial.apiKey() : "");
            txtApiKey.setEchoChar('*');

            // STDIO group
            grpStdio = new Group(container, SWT.NONE);
            grpStdio.setText("STDIO / Local Process Settings");
            grpStdio.setLayout(new GridLayout(2, false));
            var grpStdioGd = new GridData(SWT.FILL, SWT.FILL, true, false);
            grpStdioGd.horizontalSpan = 2;
            grpStdio.setLayoutData(grpStdioGd);

            addLabel(grpStdio, "Command:");
            txtCommand = addText(grpStdio, initial != null ? initial.command() : "uvx");

            addLabel(grpStdio, "Arguments:");
            txtArgs = addText(grpStdio, initial != null ? initial.args() : "");

            addLabel(grpStdio, "Environment Variables:");
            txtEnvVars = new Text(grpStdio, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
            txtEnvVars.setText(initial != null ? initial.envVars() : "");
            var envGd = new GridData(SWT.FILL, SWT.FILL, true, false);
            envGd.heightHint = 70;
            txtEnvVars.setLayoutData(envGd);
            addLabel(grpStdio, "");
            var envHint = new Label(grpStdio, SWT.NONE);
            envHint.setText("One KEY=VALUE per line, e.g.  DDG_SAFE_SEARCH=STRICT");
            envHint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            // Wire type toggle
            cmbType.addListener(SWT.Selection, e -> applyTypeVisibility(container));
            applyTypeVisibility(container);

            return area;
        }

        private void applyTypeVisibility(Composite container) {
            boolean stdio = cmbType.getSelectionIndex() == 1;
            grpHttp.setVisible(!stdio);
            ((GridData) grpHttp.getLayoutData()).exclude = stdio;
            grpStdio.setVisible(stdio);
            ((GridData) grpStdio.getLayoutData()).exclude = !stdio;
            container.layout(true, true);
            // Resize the shell to fit the new layout
            getShell().pack();
        }

        private void addLabel(Composite parent, String text) {
            var lbl = new Label(parent, SWT.NONE);
            lbl.setText(text);
            lbl.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        }

        private Text addText(Composite parent, String value) {
            var txt = new Text(parent, SWT.BORDER);
            txt.setText(value != null ? value : "");
            txt.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            return txt;
        }

        @Override
        protected void okPressed() {
            boolean stdio = cmbType.getSelectionIndex() == 1;
            result = new McpServerConfig(
                    txtName.getText().trim(),
                    stdio ? McpServerConfig.McpTransportType.STDIO : McpServerConfig.McpTransportType.HTTP,
                    stdio ? "" : txtUrl.getText().trim(),
                    stdio ? "" : txtApiKey.getText(),
                    txtProtocol.getText().trim(),
                    stdio ? txtCommand.getText().trim() : "",
                    stdio ? txtArgs.getText().trim() : "",
                    stdio ? txtEnvVars.getText() : ""
            );
            super.okPressed();
        }

        McpServerConfig getResult() {
            return result;
        }

        @Override
        protected boolean isResizable() {
            return true;
        }

    }
}
