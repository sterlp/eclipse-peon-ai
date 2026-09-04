package org.sterl.llmpeon.test;

import org.eclipse.ui.console.IConsoleDocumentPartitioner;
import org.eclipse.ui.console.TextConsole;

public class PeonTestConsole extends TextConsole {

    public PeonTestConsole(String name) {
        super(name, null, null, false);
    }

    @Override
    protected IConsoleDocumentPartitioner getPartitioner() {
        return null;
    }

    public void setContent(String text) {
        getDocument().set(text);
    }
}
