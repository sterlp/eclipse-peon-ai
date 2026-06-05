package org.sterl.llmpeon.parts.log;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.helpers.MessageFormatter;

public class EclipseSlf4jLogger extends AbstractLogger {

    private static final long serialVersionUID = 1L;
    private static final String PLUGIN_ID = "org.sterl.llmpeon";

    private final ILog log;

    public EclipseSlf4jLogger(String name) {
        this.name = name;
        ILog resolved = null;
        try {
            var bundle = Platform.getBundle(name);
            if (bundle != null) resolved = Platform.getLog(bundle);
        } catch (Exception ignored) {}
        this.log = resolved != null ? resolved
                : Platform.getLog(Platform.getBundle(PLUGIN_ID));
    }

    @Override public boolean isTraceEnabled()            { return false; }
    @Override public boolean isTraceEnabled(Marker m)    { return false; }
    @Override public boolean isDebugEnabled()            { return true;  }
    @Override public boolean isDebugEnabled(Marker m)    { return true;  }
    @Override public boolean isInfoEnabled()             { return true;  }
    @Override public boolean isInfoEnabled(Marker m)     { return true;  }
    @Override public boolean isWarnEnabled()             { return true;  }
    @Override public boolean isWarnEnabled(Marker m)     { return true;  }
    @Override public boolean isErrorEnabled()            { return true;  }
    @Override public boolean isErrorEnabled(Marker m)    { return true;  }

    @Override
    protected String getFullyQualifiedCallerName() { return null; }

    @Override
    protected void handleNormalizedLoggingCall(
            Level level, Marker marker,
            String messagePattern, Object[] arguments, Throwable t) {

        // AbstractLogger already extracted the Throwable and trimmed args for us
        var msg = arguments != null && arguments.length > 0
                ? MessageFormatter.basicArrayFormat(messagePattern, arguments)
                : messagePattern;

        switch (level) {
            case TRACE -> { /* skip */ }
            case DEBUG -> log.info(msg, t);
            case INFO  -> log.info(msg, t);
            case WARN  -> log.warn(msg, t);
            case ERROR -> log.error(msg, t);
        }
    }
}