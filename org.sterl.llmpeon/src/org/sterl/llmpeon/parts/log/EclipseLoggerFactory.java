package org.sterl.llmpeon.parts.log;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class EclipseLoggerFactory implements ILoggerFactory {

    private final ConcurrentHashMap<String, Logger> cache = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(String name) {
        return cache.computeIfAbsent(name, EclipseSlf4jLogger::new);
    }
}
