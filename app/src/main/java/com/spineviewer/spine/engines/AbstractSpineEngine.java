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
            cacheDir = new File(context.getCacheDir(), "spine_tmp");
            cacheDir.mkdirs();

            String skelName = getFileNameFromUri(skeletonUri);
            if (skelName == null) skelName = "skeleton";

            long skelSize = cacheManager.getFileSize(skeletonUri);
            long skelModified = cacheManager.getLastModified(skeletonUri);

            if (cacheManager.isCacheValid(skeletonUri, skelSize, skelModified)) {
                Log.d(TAG, "Cache valid, using cached files for " + skelName);
                File cachedSkel = cacheManager.getCachedFile(skeletonUri, skelName);
                skeletonFileHandle = new FileHandle(cachedSkel);
            } else {
                Log.d(TAG, "Cache invalid, copying skeleton file for " + skelName);
                File skelFile = cacheManager.copyUriToCache(skeletonUri, skelName, skelSize, skelModified);
                skeletonFileHandle = new FileHandle(skelFile);
            }

            if (atlasUri != null) {
                String atlasName = getFileNameFromUri(atlasUri);
                if (atlasName == null) atlasName = "skeleton.atlas";
                long atlasSize = cacheManager.getFileSize(atlasUri);
                long atlasModified = cacheManager.getLastModified(atlasUri);

                if (cacheManager.isCacheValid(atlasUri, atlasSize, atlasModified)) {
                    Log.d(TAG, "Atlas cache valid, using cached file");
                    File cachedAtlas = cacheManager.getCachedFile(atlasUri, atlasName);
                    atlasFileHandle = new FileHandle(cachedAtlas);
                } else {
                    Log.d(TAG, "Atlas cache invalid, copying atlas file");
                    File atlasFile = cacheManager.copyUriToCache(atlasUri, atlasName, atlasSize, atlasModified);
                    atlasFileHandle = new FileHandle(atlasFile);
                }

                ensureTexturesCached(atlasFileHandle.file());
            }

            loadSkeleton();
            updateRendererAlpha();
        } catch (Exception e) {
            notifyError("Failed to load skeleton: " + e.getMessage());
            Log.e(TAG, "create() error", e);
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
                copyTextureToCache(uri, name);
            }
            return;
        }

        for (String texName : textureNames) {
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
                copyTextureToCache(texUri, texName);
            } else {
                Log.w(TAG, "Could not find URI for texture: " + texName);
            }
        }
    }

    private void copyTextureToCache(Uri uri, String fileName) {
        try {
            if (cacheManager.isFileCached(uri, fileName)) {
                Log.d(TAG, "Texture already cached: " + fileName);
                File cachedFile = cacheManager.getCachedFile(uri, fileName);
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    return;
                }
            }
            Log.d(TAG, "Copying texture to cache: " + fileName);
            long fileSize = cacheManager.getFileSize(uri);
            long lastModified = cacheManager.getLastModified(uri);
            File temp = cacheManager.copyUriToCache(uri, fileName, fileSize, lastModified);
            scaleTextureIfNeeded(temp);
        } catch (Exception e) {
            Log.w(TAG, "Could not cache texture " + fileName + ": " + e.getMessage());
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
