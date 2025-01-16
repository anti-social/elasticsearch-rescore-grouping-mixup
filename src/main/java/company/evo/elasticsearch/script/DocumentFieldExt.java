package company.evo.elasticsearch.script;

import org.elasticsearch.common.document.DocumentField;

public final class DocumentFieldExt {
    public static <V> V getValueOrDefault(DocumentField hit, V defaultValue) {
        V value = hit.getValue();
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public static Double doubleValue(DocumentField hit) {
        return (Double) hit.getValue();
    }

    public static double doubleValueOrDefault(DocumentField hit, double defaultValue) {
        Double value = DocumentFieldExt.doubleValue(hit);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public static Long longValue(DocumentField hit) {
        return (Long) hit.getValue();
    }

    public static long longValueOrDefault(DocumentField hit, long defaultValue) {
        Long value = DocumentFieldExt.longValue(hit);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public static Boolean booleanValue(DocumentField hit) {
        return (Boolean) hit.getValue();
    }

    public static boolean booleanValueOrDefault(DocumentField hit, boolean defaultValue) {
        Boolean value = DocumentFieldExt.booleanValue(hit);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public static String stringValue(DocumentField hit) {
        return (String) hit.getValue();
    }

    public static String stringValueOrDefault(DocumentField hit, String defaultValue) {
        String value = DocumentFieldExt.stringValue(hit);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }
}
