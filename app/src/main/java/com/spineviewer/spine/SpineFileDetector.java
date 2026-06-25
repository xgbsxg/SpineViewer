package com.spineviewer.spine;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Detects the Spine version from a skeleton file (.json or .skel).
 *
 * JSON format: look for top-level "skeleton" → "spine" field.
 * Binary format: header has a specific structure:
 *   - hash string (null-terminated or length-prefixed)
 *   - version string (null-terminated or length-prefixed)
 *   Layout changed slightly across versions but the version string is
 *   always near the start of the file after the hash.
 */
public class SpineFileDetector {
    private static final String TAG = "SpineFileDetector";

    public static class DetectionResult {
        public final SpineVersion detectedVersion;
        public final String rawVersionString;
        public final boolean isBinaryFormat;
        public final String errorMessage;

        public DetectionResult(SpineVersion version, String raw, boolean binary, String error) {
            this.detectedVersion = version;
            this.rawVersionString = raw;
            this.isBinaryFormat = binary;
            this.errorMessage = error;
        }

        public boolean isSuccess() {
            return detectedVersion != null && errorMessage == null;
        }
    }

    /**
     * Detect version from a URI (SAF or file URI).
     */
    public static DetectionResult detect(Context context, Uri uri) {
        String fileName = getFileName(context, uri);
        boolean looksLikeJson = fileName != null && fileName.toLowerCase().endsWith(".json");
        boolean looksLikeBinary = fileName != null &&
                (fileName.toLowerCase().endsWith(".skel") || fileName.toLowerCase().endsWith(".skel.bytes"));

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return new DetectionResult(null, null, false, "Cannot open file");
            }
            byte[] header = new byte[512];
            int read = readFully(is, header);
            if (read < 4) {
                return new DetectionResult(null, null, false, "File too small");
            }

            // Attempt JSON detection first if it looks like JSON or starts with '{'
            if (looksLikeJson || header[0] == '{') {
                String text = new String(header, 0, read, StandardCharsets.UTF_8);
                DetectionResult result = detectFromJson(text);
                if (result != null) return result;
            }

            // Try binary detection
            if (looksLikeBinary || !looksLikeJson) {
                DetectionResult result = detectFromBinary(header, read);
                if (result != null) return result;
            }

            // Last attempt: try JSON regardless
            String text = new String(header, 0, read, StandardCharsets.UTF_8);
            DetectionResult result = detectFromJson(text);
            if (result != null) return result;

            return new DetectionResult(null, null, false, "Could not detect version from file header");
        } catch (IOException e) {
            Log.e(TAG, "Error reading file", e);
            return new DetectionResult(null, null, false, "IO error: " + e.getMessage());
        }
    }

    /**
     * Detect version from JSON skeleton text.
     * Looks for: { "skeleton": { "spine": "4.1.23", ... } }
     */
    private static DetectionResult detectFromJson(String text) {
        try {
            // Quick string search first (faster than full JSON parse)
            int skeletonIdx = text.indexOf("\"skeleton\"");
            if (skeletonIdx < 0) return null;
            int spineIdx = text.indexOf("\"spine\"", skeletonIdx);
            if (spineIdx < 0) return null;
            int colonIdx = text.indexOf(':', spineIdx + 7);
            if (colonIdx < 0) return null;
            int quoteStart = text.indexOf('"', colonIdx + 1);
            if (quoteStart < 0) return null;
            int quoteEnd = text.indexOf('"', quoteStart + 1);
            if (quoteEnd < 0) return null;
            String versionStr = text.substring(quoteStart + 1, quoteEnd);
            if (versionStr.isEmpty()) return null;
            SpineVersion version = SpineVersion.fromVersionString(versionStr);
            return new DetectionResult(version, versionStr, false, null);
        } catch (Exception e) {
            Log.w(TAG, "JSON detection failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Detect version from binary skeleton file header.
     *
     * Spine binary format changed across versions:
     *   New format (4.x): [hash: int64/long 8 bytes LE] [version: varint-string] ...
     *   Old format (3.x): [hash: varint-string]          [version: varint-string] ...
     *
     * We try the old format first (more distinctive — first string is a readable hash),
     * then the new format (skip 8 bytes). Only accepts version strings that start with a digit.
     */
    private static DetectionResult detectFromBinary(byte[] data, int length) {
        if (length < 9) return null;

        // Attempt 1: old format — hash is a varint-prefixed string, skip it, then read version
        try {
            int[] pos = {0};
            String hash = readBinaryString(data, pos, length);
            if (hash != null && pos[0] < length) {
                String version = readBinaryString(data, pos, length);
                if (isValidVersionString(version)) {
                    SpineVersion sv = SpineVersion.fromVersionString(version);
                    return new DetectionResult(sv, version, true, null);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Old-format binary detection failed: " + e.getMessage());
        }

        // Attempt 2: new format — hash is an 8-byte long, skip it, then read version
        try {
            int[] pos = {8};
            String version = readBinaryString(data, pos, length);
            if (isValidVersionString(version)) {
                SpineVersion sv = SpineVersion.fromVersionString(version);
                return new DetectionResult(sv, version, true, null);
            }
        } catch (Exception e) {
            Log.w(TAG, "New-format binary detection failed: " + e.getMessage());
        }

        return null;
    }

    /** Returns true if the string looks like a spine version (starts with a digit). */
    private static boolean isValidVersionString(String s) {
        return s != null && !s.isEmpty() && s.charAt(0) >= '0' && s.charAt(0) <= '9';
    }

    /**
     * Read a spine binary string: varint length prefix followed by UTF-8 bytes.
     * Null/empty strings are encoded as the varint value 0.
     * Non-null strings: varint value = byteCount + 1, followed by byteCount UTF-8 bytes.
     * Varint format: 7 data bits per byte, MSB indicates continuation (matches SkeletonBinary.readInt(true)).
     */
    private static String readBinaryString(byte[] data, int[] pos, int length) {
        if (pos[0] >= length) return null;

        // Read varint: 7 bits per byte, MSB=1 means more bytes follow
        int value = 0;
        int shift = 0;
        while (pos[0] < length) {
            int b = data[pos[0]++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 28) return null; // guard against corrupt data
        }

        // value == 0 encodes null (not used for hash/version in practice, but handle it)
        if (value == 0) return "";

        // value = byteCount + 1
        int byteCount = value - 1;
        if (byteCount <= 0 || pos[0] + byteCount > length) return null;

        String s = new String(data, pos[0], byteCount, StandardCharsets.UTF_8);
        pos[0] += byteCount;
        return s;
    }

    private static String getFileName(Context context, Uri uri) {
        String path = uri.getPath();
        if (path == null) return null;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static int readFully(InputStream is, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int read = is.read(buf, total, buf.length - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }
}
