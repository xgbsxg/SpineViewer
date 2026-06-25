package com.spineviewer.spine;

import com.spineviewer.spine.engines.*;

/**
 * Creates the appropriate SpineViewerEngine for a given version.
 */
public class SpineEngineFactory {
    public static SpineViewerEngine create(SpineVersion version) {
        switch (version) {
            case V1_6: return new SpineEngine16();
            case V1_7: return new SpineEngine17();
            case V2_0: return new SpineEngine20();
            case V2_1: return new SpineEngine21();
            case V3_1: return new SpineEngine31();
            case V3_4: return new SpineEngine34();
            case V3_5: return new SpineEngine35();
            case V3_6: return new SpineEngine36();
            case V3_7: return new SpineEngine37();
            case V3_8: return new SpineEngine38();
            case V4_0: return new SpineEngine40();
            case V4_1: return new SpineEngine41();
            case V4_2: return new SpineEngine42();
            case V4_3: return new SpineEngine43();
            default:   return new SpineEngine43();
        }
    }
}
