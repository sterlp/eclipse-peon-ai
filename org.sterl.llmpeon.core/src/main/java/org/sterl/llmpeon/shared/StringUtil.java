package org.sterl.llmpeon.shared;

public class StringUtil {

    public static String strip(String value) {
        if (value == null || value.length() == 0) return value;
        return value.strip();
    }
    
    public static String stripToNull(String value) {
        value = strip(value);
        if (value == null || value.length() == 0) return null;
        return value.strip();
    }
    
    public static boolean haValue(String value) {
        if (stripToNull(value) == null) return false;
        return true;
    }
    
    public static boolean hasNoValue(String value) {
        return !haValue(value);
    }
}
