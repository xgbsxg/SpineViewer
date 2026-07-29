package com.spineviewer.utils;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

public class CacheManager {

    private static final String CACHE_DIR_NAME = "spine_cache";

    private final Context context;
    private final File cacheRoot;

    public CacheManager(Context context) {
        this.context = context;
        this.cacheRoot = new File(context.getCacheDir(), CACHE_DIR_NAME);
        if (!cacheRoot.exists()) {
            cacheRoot.mkdirs();
        }
    }

    public String generateCacheKey(Uri uri) {
        try {
            String uriString = uri.toString();
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(uriString.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(uri.toString().hashCode());
        }
    }

    public File getCacheDirForUri(Uri uri) {
        String key = generateCacheKey(uri);
        File dir = new File(cacheRoot, key);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public boolean isCacheValid(Uri uri, long expectedFileSize, long expectedLastModified) {
        File cacheIndex = getCacheIndexFile(uri);
        if (!cacheIndex.exists()) {
            return false;
        }
        try {
            FileInputStream fis = new FileInputStream(cacheIndex);
            byte[] data = new byte[(int) cacheIndex.length()];
            fis.read(data);
            fis.close();
            String content = new String(data, "UTF-8");
            String[] parts = content.split("\\|");
            if (parts.length == 2) {
                long cachedSize = Long.parseLong(parts[0]);
                long cachedModified = Long.parseLong(parts[1]);
                return cachedSize == expectedFileSize && cachedModified == expectedLastModified;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public void saveCacheIndex(Uri uri, long fileSize, long lastModified) {
        File cacheIndex = getCacheIndexFile(uri);
        try {
            FileOutputStream fos = new FileOutputStream(cacheIndex);
            String content = fileSize + "|" + lastModified;
            fos.write(content.getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
        }
    }

    public File getCachedFile(Uri uri, String fileName) {
        File cacheDir = getCacheDirForUri(uri);
        return new File(cacheDir, fileName);
    }

    public boolean isFileCached(Uri uri, String fileName) {
        File cachedFile = getCachedFile(uri, fileName);
        return cachedFile.exists();
    }

    public File copyUriToCache(Uri uri, String fileName, long fileSize, long lastModified) throws Exception {
        File cacheDir = getCacheDirForUri(uri);
        File out = new File(cacheDir, fileName);

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (is == null) throw new Exception("Cannot open URI: " + uri);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        }

        saveCacheIndex(uri, fileSize, lastModified);
        return out;
    }

    public void clearCacheForUri(Uri uri) {
        File cacheDir = getCacheDirForUri(uri);
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            cacheDir.delete();
        }
    }

    public void clearAllCache() {
        if (cacheRoot.exists()) {
            File[] dirs = cacheRoot.listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            f.delete();
                        }
                    }
                    dir.delete();
                }
            }
        }
    }

    public long getCacheSize() {
        if (!cacheRoot.exists()) {
            return 0;
        }
        return getFolderSize(cacheRoot);
    }

    private long getFolderSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                size += getFolderSize(f);
            } else {
                size += f.length();
            }
        }
        return size;
    }

    private File getCacheIndexFile(Uri uri) {
        File cacheDir = getCacheDirForUri(uri);
        return new File(cacheDir, ".cache_index");
    }

    public long getFileSize(Uri uri) {
        try {
            android.database.Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{DocumentsContract.Document.COLUMN_SIZE},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                long size = cursor.getLong(0);
                cursor.close();
                return size;
            }
        } catch (Exception e) {
        }
        return 0;
    }

    public long getLastModified(Uri uri) {
        try {
            android.database.Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{DocumentsContract.Document.COLUMN_LAST_MODIFIED},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                long modified = cursor.getLong(0);
                cursor.close();
                return modified;
            }
        } catch (Exception e) {
        }
        return 0;
    }
}
