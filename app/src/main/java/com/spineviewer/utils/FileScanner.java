package com.spineviewer.utils;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.spineviewer.spine.SpineFileDetector;
import com.spineviewer.spine.SpineFileInfo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            siblingUris.add(f.getUri());
        }

        for (DocumentFile skelFile : skeletons) {
            String fn = skelFile.getName();
            if (fn == null) continue;
            String base = baseName(fn);
            boolean isBinary = fn.toLowerCase().endsWith(".skel") || fn.toLowerCase().endsWith(".bytes");

            Uri atlasUri = atlasMap.get(base);
            if (atlasUri == null) {
                for (Map.Entry<String, Uri> e : atlasMap.entrySet()) {
                    if (base.startsWith(e.getKey()) || e.getKey().startsWith(base)) {
                        atlasUri = e.getValue();
                        break;
                    }
                }
            }
            if (atlasUri == null && !atlasMap.isEmpty()) {
                atlasUri = atlasMap.values().iterator().next();
            }

            SpineFileInfo info = new SpineFileInfo(skelFile.getUri(), atlasUri, base, isBinary);
            info.fileSizeBytes = skelFile.length();
            info.siblingUris.addAll(siblingUris);

            SpineFileDetector.DetectionResult detection = SpineFileDetector.detect(context, skelFile.getUri());
            info.detectedVersion = detection.detectedVersion;
            info.selectedVersion = detection.detectedVersion;
            info.rawVersionString = detection.rawVersionString;

            results.add(info);
            Log.d(TAG, "Found: " + base + " version=" + info.rawVersionString + " atlas=" + (atlasUri != null));
        }

        for (DocumentFile f : children) {
            if (f.isDirectory()) {
                scanDirectory(context, f, results);
            }
        }
    }

    private static boolean couldBeSpineJson(Context context, DocumentFile f) {
        try (InputStream is = context.getContentResolver().openInputStream(f.getUri())) {
            if (is == null) return false;
            byte[] buf = new byte[256];
            int n = is.read(buf);
            if (n < 2) return false;
            String sample = new String(buf, 0, n);
            return sample.contains("\"skeleton\"") || sample.contains("\"bones\"");
        } catch (Exception e) {
            return false;
        }
    }

    private static String baseName(String fileName) {
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
