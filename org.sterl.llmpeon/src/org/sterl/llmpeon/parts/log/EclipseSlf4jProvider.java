package org.sterl.llmpeon.parts.log;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public class EclipseSlf4jProvider implements SLF4JServiceProvider {

    public static final String REQUESTED_API_VERSION = "2.0.99";

    private ILoggerFactory loggerFactory;
    private IMarkerFactory markerFactory;
    private MDCAdapter mdcAdapter;

    @Override
    public void initialize() {
        loggerFactory = new EclipseLoggerFactory();
        markerFactory = new BasicMarkerFactory();
        mdcAdapter    = new NOPMDCAdapter();
    }

    @Override public ILoggerFactory    getLoggerFactory()  { return loggerFactory; }
    @Override public IMarkerFactory    getMarkerFactory()  { return markerFactory; }
    @Override public MDCAdapter        getMDCAdapter()     { return mdcAdapter; }
    @Override public String            getRequestedApiVersion() { return REQUESTED_API_VERSION; }
}
