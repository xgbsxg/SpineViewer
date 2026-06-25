package com.spineviewer.utils;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.spineviewer.spine.SpineFileDetector;
import com.spineviewer.spine.SpineFileInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursively scans a SAF document tree for Spine skeleton files (.json, .skel)
 * and tries to pair each with a corresponding .atlas file.
 */
public class FileScanner {
    private static final String TAG = "FileScanner";

    public static List<SpineFileInfo> scanForSpineFiles(Context context, Uri treeUri) {
        List<SpineFileInfo> results = new ArrayList<>();
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null) return results;

        scanDirectory(context, root, results);
        return results;
    }

    private static void scanDirectory(Context context, DocumentFile dir, List<SpineFileInfo> results) {
        DocumentFile[] children = dir.listFiles();
        if (children == null) return;

        // Build a map of name (no extension) → files for pairing
        Map<String, Uri> atlasMap = new HashMap<>();
        Map<String, Uri> pngMap = new HashMap<>();
        List<DocumentFile> skeletons = new ArrayList<>();
        List<Uri> siblingUris = new ArrayList<>();

        for (DocumentFile f : children) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name == null) continue;
            String lower = name.toLowerCase();

            if (lower.endsWith(".skel") || lower.endsWith(".skel.bytes")) {
                skeletons.add(f);
            } else if (lower.endsWith(".json") && !lower.endsWith(".fnt") && couldBeSpineJson(context, f)) {
                skeletons.add(f);
            } else if (lower.endsWith(".atlas")) {
                String base = baseName(name);
                atlasMap.put(base, f.getUri());
            } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".webp")) {
                String base = baseName(name);
                pngMap.put(base, f.getUri());
            }
            // Collect URIs of all non-directory files in this directory
            siblingUris.add(f.getUri());
        }

        // Pair skeletons with their atlas
        for (DocumentFile skelFile : skeletons) {
            String fn = skelFile.getName();
            if (fn == null) continue;
            String base = baseName(fn);
            boolean isBinary = fn.toLowerCase().endsWith(".skel") || fn.toLowerCase().endsWith(".bytes");

            // Try to find atlas: exact base match, or any atlas in same folder
            Uri atlasUri = atlasMap.get(base);
            if (atlasUri == null) {
                // Try removing extra suffixes like "skeleton" suffix
                for (Map.Entry<String, Uri> e : atlasMap.entrySet()) {
                    if (base.startsWith(e.getKey()) || e.getKey().startsWith(base)) {
                        atlasUri = e.getValue();
                        break;
                    }
                }
            }
            if (atlasUri == null && !atlasMap.isEmpty()) {
                // Just take the first atlas in the same folder
                atlasUri = atlasMap.values().iterator().next();
            }

            SpineFileInfo info = new SpineFileInfo(skelFile.getUri(), atlasUri, base, isBinary);
            info.fileSizeBytes = skelFile.length();
            info.siblingUris.addAll(siblingUris);

            // Detect version
            SpineFileDetector.DetectionResult detection = SpineFileDetector.detect(context, skelFile.getUri());
            info.detectedVersion = detection.detectedVersion;
            info.selectedVersion = detection.detectedVersion;
            info.rawVersionString = detection.rawVersionString;

            results.add(info);
            Log.d(TAG, "Found: " + base + " version=" + info.rawVersionString + " atlas=" + (atlasUri != null));
        }

        // Recurse into subdirectories
        for (DocumentFile f : children) {
            if (f.isDirectory()) {
                scanDirectory(context, f, results);
            }
        }
    }

    /**
     * Quick check if a JSON file is likely a Spine skeleton (looks for "skeleton" key).
     */
    private static boolean couldBeSpineJson(Context context, DocumentFile f) {
        try (java.io.InputStream is = context.getContentResolver().openInputStream(f.getUri())) {
            if (is == null) return false;
            byte[] buf = new byte[256];
            int n = is.read(buf);
            if (n < 2) return false;
            String sample = new String(buf, 0, n);
            // A spine JSON starts with { and contains "skeleton" or "bones" near the top
            return sample.contains("\"skeleton\"") || sample.contains("\"bones\"");
        } catch (Exception e) {
            return false;
        }
    }

    private static String baseName(String fileName) {
        // Remove known extensions
        String name = fileName;
        for (String ext : new String[]{".skel.bytes", ".skel", ".json", ".atlas", ".png", ".jpg", ".webp"}) {
            if (name.toLowerCase().endsWith(ext)) {
                name = name.substring(0, name.length() - ext.length());
                break;
            }
        }
        return name;
    }
}
