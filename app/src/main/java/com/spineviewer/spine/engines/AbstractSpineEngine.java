package com.spineviewer.spine.engines;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.BufferUtils;

import com.spineviewer.spine.SpineViewerEngine;
import com.spineviewer.utils.CacheManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractSpineEngine extends SpineViewerEngine {
    private static final String TAG = "AbstractSpineEngine";

    protected ShapeRenderer shapeRenderer;
    protected File cacheDir;
    protected FileHandle skeletonFileHandle;
    protected FileHandle atlasFileHandle;

    protected static final Color BG_COLOR = new Color(0.15f, 0.15f, 0.18f, 1f);

    private int maxTextureSize = 2048;

    protected boolean premultipliedAlpha = false;

    private CacheManager cacheManager;
    private File commonCacheDir;

    @Override
    public void create() {
        batch = new PolygonSpriteBatch();
        shapeRenderer = new ShapeRenderer();

        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, w, h);
        camX = w / 2f;
        camY = h / 3f;
        camera.position.set(camX, camY, 0);

        try {
            IntBuffer buffer = BufferUtils.newIntBuffer(16);
            Gdx.gl20.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, buffer);
            maxTextureSize = buffer.get(0);
            Log.d(TAG, "Max texture size: " + maxTextureSize);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get max texture size, using default 2048", e);
            maxTextureSize = 2048;
        }

        try {
            cacheManager = new CacheManager(context);

            String skelName = getFileNameFromUri(skeletonUri);
            if (skelName == null) skelName = "skeleton";

            String cacheKey = cacheManager.generateCacheKey(skeletonUri);
            commonCacheDir = new File(context.getCacheDir(), "spine_cache/" + cacheKey);
            commonCacheDir.mkdirs();

            long skelSize = cacheManager.getFileSize(skeletonUri);
            long skelModified = cacheManager.getLastModified(skeletonUri);
            File skelCacheIndex = new File(commonCacheDir, ".cache_index");

            boolean cacheValid = false;
            if (skelCacheIndex.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(skelCacheIndex);
                    byte[] data = new byte[(int) skelCacheIndex.length()];
                    fis.read(data);
                    fis.close();
                    String content = new String(data, "UTF-8");
                    String[] parts = content.split("\\|");
                    if (parts.length == 2) {
                        long cachedSize = Long.parseLong(parts[0]);
                        long cachedModified = Long.parseLong(parts[1]);
                        cacheValid = (cachedSize == skelSize && cachedModified == skelModified);
                    }
                } catch (Exception e) {
                }
            }

            if (cacheValid) {
                Log.d(TAG, "Cache valid, using cached files for " + skelName);
                File cachedSkel = new File(commonCacheDir, skelName);
                skeletonFileHandle = new FileHandle(cachedSkel);
            } else {
                Log.d(TAG, "Cache invalid, copying files for " + skelName);
                copyFileTo(skeletonUri, new File(commonCacheDir, skelName));
                skeletonFileHandle = new FileHandle(new File(commonCacheDir, skelName));
                saveSkeletonCacheIndex(skelCacheIndex, skelSize, skelModified);
            }

            if (atlasUri != null) {
                String atlasName = getFileNameFromUri(atlasUri);
                if (atlasName == null) atlasName = "skeleton.atlas";
                File atlasFile = new File(commonCacheDir, atlasName);
                if (!atlasFile.exists()) {
                    Log.d(TAG, "Copying atlas file to cache");
                    copyFileTo(atlasUri, atlasFile);
                }
                atlasFileHandle = new FileHandle(atlasFile);
                ensureTexturesCached(atlasFile);
            }

            cacheDir = new File(context.getCacheDir(), "spine_tmp");
            cacheDir.mkdirs();

            loadSkeleton();
            updateRendererAlpha();
        } catch (Exception e) {
            notifyError("Failed to load skeleton: " + e.getMessage());
            Log.e(TAG, "create() error", e);
        }
    }

    private void saveSkeletonCacheIndex(File indexFile, long fileSize, long lastModified) {
        try {
            FileOutputStream fos = new FileOutputStream(indexFile);
            String content = fileSize + "|" + lastModified;
            fos.write(content.getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
        }
    }

    private void copyFileTo(Uri sourceUri, File destFile) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            if (is == null) throw new Exception("Cannot open URI: " + sourceUri);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        }
    }

    protected abstract void loadSkeleton() throws Exception;

    @Override
    public void render() {
        Gdx.gl.glClearColor(BG_COLOR.r, BG_COLOR.g, BG_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float delta = paused ? 0f : Math.min(Gdx.graphics.getDeltaTime(), 0.033f);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        try {
            renderSkeleton(delta);
        } catch (Exception e) {
            Log.e(TAG, "Render error", e);
            notifyError("Render error: " + e.getMessage());
        }

        if (showBones) {
            ShapeRenderer debugShapes = getDebugShapeRenderer();
            if (debugShapes != null) {
                debugShapes.setProjectionMatrix(camera.combined);
            }
            renderDebug();
        }
    }

    protected abstract void renderSkeleton(float delta);

    protected void renderDebug() {}

    @Override
    public void dispose() {
        super.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (cacheDir != null) {
            deleteRecursive(cacheDir);
        }
    }

    private void ensureTexturesCached(File atlasFile) {
        if (atlasFile == null) return;

        Set<String> textureNames = new HashSet<>();
        try {
            String atlasContent = readFileAsString(atlasFile);
            String[] lines = atlasContent.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.endsWith(".png") || trimmed.endsWith(".jpg") || trimmed.endsWith(".webp")) {
                    textureNames.add(trimmed);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse atlas", e);
        }

        if (textureNames.isEmpty() && textureUris != null) {
            Log.d(TAG, "No texture names in atlas, copying all sibling files");
            for (Uri uri : textureUris) {
                String name = getFileNameFromUri(uri);
                if (name == null) continue;
                if (name.equals(skeletonFileHandle.name()) || name.equals(atlasFileHandle.name())) continue;
                File texFile = new File(commonCacheDir, name);
                if (!texFile.exists()) {
                    try {
                        copyFileTo(uri, texFile);
                        scaleTextureIfNeeded(texFile);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not cache texture " + name + ": " + e.getMessage());
                    }
                }
            }
            return;
        }

        for (String texName : textureNames) {
            File texFile = new File(commonCacheDir, texName);
            if (texFile.exists() && texFile.length() > 0) {
                continue;
            }

            Uri texUri = null;
            if (textureUris != null) {
                for (Uri uri : textureUris) {
                    String name = getFileNameFromUri(uri);
                    if (texName.equals(name)) {
                        texUri = uri;
                        break;
                    }
                }
            }
            if (texUri == null) {
                texUri = buildSiblingUri(atlasUri, texName);
            }
            if (texUri != null) {
                try {
                    copyFileTo(texUri, texFile);
                    scaleTextureIfNeeded(texFile);
                } catch (Exception e) {
                    Log.w(TAG, "Could not cache texture " + texName + ": " + e.getMessage());
                }
            } else {
                Log.w(TAG, "Could not find URI for texture: " + texName);
            }
        }
    }

    private String readFileAsString(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(data, "UTF-8");
        } finally {
            fis.close();
        }
    }

    private void scaleTextureIfNeeded(File imageFile) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), opts);
        int width = opts.outWidth;
        int height = opts.outHeight;
        if (width <= 0 || height <= 0) return;

        if (width > maxTextureSize || height > maxTextureSize) {
            int sampleSize = 1;
            while (width / sampleSize > maxTextureSize || height / sampleSize > maxTextureSize) {
                sampleSize *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), opts);
            if (bitmap == null) return;

            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                Log.d(TAG, "Scaled texture from " + width + "x" + height + " to " +
                        bitmap.getWidth() + "x" + bitmap.getHeight() +
                        " (sampleSize=" + sampleSize + ")");
            } catch (IOException e) {
                Log.w(TAG, "Failed to save scaled texture", e);
            } finally {
                bitmap.recycle();
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (Exception ignored) {
        }
        try {
            String lastSeg = uri.getLastPathSegment();
            if (lastSeg != null) {
                int idx = lastSeg.lastIndexOf('/');
                return idx >= 0 ? lastSeg.substring(idx + 1) : lastSeg;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Uri extractTreeUri(Uri documentUri) {
        java.util.List<String> segments = documentUri.getPathSegments();
        if (segments.size() >= 2 && "tree".equals(segments.get(0))) {
            return new Uri.Builder()
                    .scheme(documentUri.getScheme())
                    .authority(documentUri.getAuthority())
                    .appendPath("tree")
                    .appendPath(segments.get(1))
                    .build();
        }
        return null;
    }

    protected Uri buildSiblingUri(Uri uri, String siblingName) {
        try {
            String docId = DocumentsContract.getDocumentId(uri);
            if (docId == null) return null;
            Uri treeUri = extractTreeUri(uri);
            if (treeUri == null) return null;
            int lastSep = docId.lastIndexOf('/');
            String parentDocId = lastSep >= 0 ? docId.substring(0, lastSep) : docId;
            String siblingDocId = parentDocId + "/" + siblingName;
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, siblingDocId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to build sibling URI for: " + siblingName);
            return null;
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) deleteRecursive(c);
        }
        f.delete();
    }

    protected void updateRendererAlpha() {
    }

    @Override
    public void setPremultipliedAlpha(boolean enabled) {
        this.premultipliedAlpha = enabled;
        if (loaded) {
            updateRendererAlpha();
        }
    }

    @Override
    public List<String> getAnimations() { return new ArrayList<>(); }
    @Override
    public List<String> getSkins() { return new ArrayList<>(); }
    @Override
    public void setAnimation(String name, boolean loop) { currentAnimation = name; looping = loop; }
    @Override
    public void setSkin(String name) {}
}
