package org.sterl.llmpeon.parts.log;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.sterl.llmpeon.parts.PeonConstants;

public class EclipseSlf4jLogger extends AbstractLogger {

    private static final long serialVersionUID = 1L;
    private static final String PLUGIN_ID = PeonConstants.PLUGIN_ID;
    private static volatile boolean isDebugMode = false;

    private final ILog log;

    public EclipseSlf4jLogger(String name) {
        this.name = name;
        ILog resolved = null;
        try {
            var bundle = Platform.getBundle(name);
            if (bundle != null) {
                resolved = Platform.getLog(bundle);
            }
        } catch (Exception ignored) {}
        this.log = resolved != null ? resolved : Platform.getLog(Platform.getBundle(PLUGIN_ID));
    }

    @Override public boolean isTraceEnabled()            { return false; }
    @Override public boolean isTraceEnabled(Marker m)    { return false; }
    @Override public boolean isDebugEnabled()            { return isDebugMode; }
    @Override public boolean isDebugEnabled(Marker m)    { return isDebugMode; }
    @Override public boolean isInfoEnabled()             { return true;  }
    @Override public boolean isInfoEnabled(Marker m)     { return true;  }
    @Override public boolean isWarnEnabled()             { return true;  }
    @Override public boolean isWarnEnabled(Marker m)     { return true;  }
    @Override public boolean isErrorEnabled()            { return true;  }
    @Override public boolean isErrorEnabled(Marker m)    { return true;  }

    @Override protected String getFullyQualifiedCallerName() { return null; }

    @Override
    protected void handleNormalizedLoggingCall(
            Level level, Marker marker,
            String messagePattern, Object[] arguments, Throwable t) {

        if (level == Level.TRACE || log == null) return;
        if (level == Level.DEBUG && !isDebugMode) return;

        var formatted = MessageFormatter.arrayFormat(messagePattern, arguments);
        String msg = formatted.getMessage();

        int severity;
        switch (level) {
            case DEBUG -> severity = IStatus.INFO;
            case INFO  -> severity = IStatus.INFO;
            case WARN  -> severity = IStatus.WARNING;
            case ERROR -> severity = IStatus.ERROR;
            default    -> severity = IStatus.OK;
        }

        try {
            var status = t != null 
                    ? new Status(severity, PLUGIN_ID, msg, t) 
                    : new Status(severity, PLUGIN_ID, msg);
            log.log(status);
        } catch (Exception e) {
            System.err.println(msg + (t != null ? " | Cause: " + t.getMessage() : ""));
        }
    }

    public static void setDebug(boolean debugMode) {
        isDebugMode = debugMode;
    }
}
