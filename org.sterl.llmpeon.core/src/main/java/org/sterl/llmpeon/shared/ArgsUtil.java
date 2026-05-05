package org.sterl.llmpeon.shared;

public class ArgsUtil {

    public static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }
    
    public static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " required and can not be null");
        }
    }
    
    /**
     * returns default if value is <code>null</code> or smaller than <code>0</code>
     */
    public static Integer getOrDefault(Integer value, int defaultValue) {
        if (value == null || value <= 0) return defaultValue;
        return value;
    }
}
