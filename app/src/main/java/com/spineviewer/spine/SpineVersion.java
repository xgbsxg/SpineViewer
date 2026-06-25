package com.spineviewer.spine;

/**
 * Enum representing all supported Spine runtime versions (oldest first).
 */
public enum SpineVersion {
    V1_6("1.6"),
    V1_7("1.7"),
    V2_0("2.0"),
    V2_1("2.1"),
    V3_1("3.1"),
    V3_4("3.4"),
    V3_5("3.5"),
    V3_6("3.6"),
    V3_7("3.7"),
    V3_8("3.8"),
    V4_0("4.0"),
    V4_1("4.1"),
    V4_2("4.2"),
    V4_3("4.3");

    private final String displayName;

    SpineVersion(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Detect version from the "spine" string embedded in skeleton files.
     * v1.6 and v1.7 don't store a version, so they return null from detection.
     */
    public static SpineVersion fromVersionString(String versionString) {
        if (versionString == null || versionString.isEmpty()) return null;
        String v = versionString.trim();
        if (v.startsWith("1.6")) return V1_6;
        if (v.startsWith("1.7")) return V1_7;
        if (v.startsWith("2.0")) return V2_0;
        if (v.startsWith("2.1")) return V2_1;
        if (v.startsWith("3.1")) return V3_1;
        if (v.startsWith("3.4")) return V3_4;
        if (v.startsWith("3.5")) return V3_5;
        if (v.startsWith("3.6")) return V3_6;
        if (v.startsWith("3.7")) return V3_7;
        if (v.startsWith("3.8")) return V3_8;
        if (v.startsWith("4.0")) return V4_0;
        if (v.startsWith("4.1")) return V4_1;
        if (v.startsWith("4.2")) return V4_2;
        if (v.startsWith("4.3")) return V4_3;
        // Numeric fallback
        try {
            String major = v.contains(".") ? v.substring(0, v.indexOf('.')) : v;
            String minor = v.contains(".") ? v.substring(v.indexOf('.') + 1) : "0";
            if (minor.contains(".")) minor = minor.substring(0, minor.indexOf('.'));
            int maj = Integer.parseInt(major);
            int min = Integer.parseInt(minor.replaceAll("[^0-9]", ""));
            if (maj == 1) return min <= 6 ? V1_6 : V1_7;
            if (maj == 2) return min == 0 ? V2_0 : V2_1;
            if (maj == 3) {
                if (min <= 1) return V3_1;
                if (min <= 4) return V3_4;
                if (min == 5) return V3_5;
                if (min == 6) return V3_6;
                if (min == 7) return V3_7;
                return V3_8;
            }
            if (maj == 4) {
                if (min == 0) return V4_0;
                if (min == 1) return V4_1;
                if (min == 2) return V4_2;
                return V4_3;
            }
        } catch (NumberFormatException ignored) {}
        return V4_3;
    }

    public SpineVersion next() {
        SpineVersion[] vals = values();
        int idx = ordinal() + 1;
        return idx < vals.length ? vals[idx] : null;
    }

    public SpineVersion previous() {
        int idx = ordinal() - 1;
        return idx >= 0 ? values()[idx] : null;
    }

    public static SpineVersion oldest() { return V1_6; }
    public static SpineVersion latest() { return V4_3; }
}
