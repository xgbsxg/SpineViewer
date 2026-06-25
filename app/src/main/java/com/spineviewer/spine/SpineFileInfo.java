package com.spineviewer.spine;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds metadata about a discovered Spine skeleton file set.
 * A complete Spine skeleton consists of:
 *  - a .json or .skel file (skeleton data)
 *  - one or more .atlas files (texture atlas descriptors)
 *  - one or more .png files (texture images)
 */
public class SpineFileInfo {
    public final Uri skeletonUri;
    public final Uri atlasUri;          // may be null if not found
    public final String name;           // display name (filename without extension)
    public final boolean isBinary;
    /** URIs of all sibling files in the same directory (used to copy textures etc). */
    public final List<Uri> siblingUris = new ArrayList<>();
    public SpineVersion detectedVersion;   // null if could not detect
    public SpineVersion selectedVersion;   // user-selected override, or == detectedVersion
    public String rawVersionString;        // e.g. "4.1.23"
    public long fileSizeBytes;

    public SpineFileInfo(Uri skeletonUri, Uri atlasUri, String name, boolean isBinary) {
        this.skeletonUri = skeletonUri;
        this.atlasUri = atlasUri;
        this.name = name;
        this.isBinary = isBinary;
    }

    public SpineVersion getEffectiveVersion() {
        return selectedVersion != null ? selectedVersion :
               detectedVersion != null ? detectedVersion :
               SpineVersion.latest();
    }

    public boolean hasAtlas() {
        return atlasUri != null;
    }

    public String getVersionLabel() {
        if (rawVersionString != null && !rawVersionString.isEmpty()) {
            return "Spine " + rawVersionString + " (runtime " + getEffectiveVersion().getDisplayName() + ")";
        }
        if (detectedVersion != null) {
            return "Spine " + detectedVersion.getDisplayName();
        }
        return "Unknown version";
    }
}
