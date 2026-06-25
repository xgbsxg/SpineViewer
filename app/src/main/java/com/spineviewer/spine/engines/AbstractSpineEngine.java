package com.spineviewer.spine.engines;

import android.content.Context;
import android.database.Cursor;
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

import com.spineviewer.spine.SpineViewerEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared base class with common rendering setup, camera, and helper utilities.
 * Each version-specific subclass overrides loadSkeleton() to use its own
 * runtime classes and implements the abstract animation/skin accessors.
 */
public abstract class AbstractSpineEngine extends SpineViewerEngine {
    private static final String TAG = "AbstractSpineEngine";

    protected ShapeRenderer shapeRenderer;
    protected File cacheDir;
    protected FileHandle skeletonFileHandle;
    protected FileHandle atlasFileHandle;

    // Background color (checkerboard-like dark gray)
    protected static final Color BG_COLOR = new Color(0.15f, 0.15f, 0.18f, 1f);

    @Override
    public void create() {
        batch = new PolygonSpriteBatch();
        shapeRenderer = new ShapeRenderer();

        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, w, h);
        camX = w / 2f;
        camY = h / 3f;  // place skeleton slightly above center
        camera.position.set(camX, camY, 0);

        // Copy URIs to temp files so libGDX FileHandle can read them
        try {
            cacheDir = new File(context.getCacheDir(), "spine_tmp");
            cacheDir.mkdirs();
            // Preserve original filename so engines can detect .skel vs .json
            String skelName = getFileNameFromUri(skeletonUri);
            if (skelName == null) skelName = "skeleton";
            File skelFile = copyUriToTemp(skeletonUri, skelName);
            skeletonFileHandle = new FileHandle(skelFile);

            if (atlasUri != null) {
                String atlasName = getFileNameFromUri(atlasUri);
                if (atlasName == null) atlasName = "skeleton.atlas";
                File atlasFile = copyUriToTemp(atlasUri, atlasName);
                atlasFileHandle = new FileHandle(atlasFile);

                // Copy any png files referenced in the atlas — look for *.png siblings
                copyAtlasTextures(atlasFile);
            }

            loadSkeleton();
        } catch (Exception e) {
            notifyError("Failed to load skeleton: " + e.getMessage());
            Log.e(TAG, "create() error", e);
        }
    }

    /**
     * Subclasses implement this to create their version-specific skeleton, atlas, renderer etc.
     */
    protected abstract void loadSkeleton() throws Exception;

    @Override
    public void render() {
        Gdx.gl.glClearColor(BG_COLOR.r, BG_COLOR.g, BG_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float delta = paused ? 0f : Math.min(Gdx.graphics.getDeltaTime(), 0.033f);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        renderSkeleton(delta);

        if (showBones) {
            com.badlogic.gdx.graphics.glutils.ShapeRenderer debugShapes = getDebugShapeRenderer();
            if (debugShapes != null) {
                debugShapes.setProjectionMatrix(camera.combined);
            }
            renderDebug();
        }
    }

    /**
     * Subclasses implement this to update AnimationState and render the SkeletonRenderer.
     */
    protected abstract void renderSkeleton(float delta);

    /**
     * Optional: render debug overlays (bones, attachment outlines).
     */
    protected void renderDebug() {}

    @Override
    public void dispose() {
        super.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        // Clean up temp files
        if (cacheDir != null) {
            deleteRecursive(cacheDir);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    protected File copyUriToTemp(Uri uri, String targetName) throws IOException {
        File out = new File(cacheDir, targetName);
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (is == null) throw new IOException("Cannot open URI: " + uri);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        }
        return out;
    }

    /**
     * Copies all sibling files from the SAF directory into the cache.
     * Uses pre-known URIs collected by FileScanner as the primary method,
     * falling back to ContentResolver directory enumeration and atlas parsing.
     */
    protected void copyAtlasTextures(File atlasFile) {
        // Primary strategy: copy all pre-known sibling URIs (from FileScanner)
        if (textureUris != null && !textureUris.isEmpty()) {
            Log.d(TAG, "Copying " + textureUris.size() + " sibling files from pre-scanned URIs");
            for (Uri uri : textureUris) {
                String name = getFileNameFromUri(uri);
                if (name == null) {
                    Log.w(TAG, "Could not extract filename from URI: " + uri);
                    continue;
                }
                // Skip skeleton and atlas — already copied with original names above
                if (name.equals(skeletonFileHandle.name()) || name.equals(atlasFileHandle.name())) continue;
                try {
                    copyUriToTemp(uri, name);
                    Log.d(TAG, "Copied: " + name);
                } catch (Exception e) {
                    Log.w(TAG, "Could not copy " + name + ": " + e.getMessage());
                }
            }
            return;
        }

        Log.w(TAG, "No pre-scanned URIs available, falling back to directory enumeration");

        // Fallback strategy: enumerate parent directory via ContentResolver
        try {
            String atlasDocId = DocumentsContract.getDocumentId(atlasUri);
            if (atlasDocId != null) {
                Uri treeUri = extractTreeUri(atlasUri);
                if (treeUri != null) {
                    int lastSep = atlasDocId.lastIndexOf('/');
                    String parentDocId = lastSep >= 0 ? atlasDocId.substring(0, lastSep) : atlasDocId;
                    Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
                    Cursor cursor = context.getContentResolver().query(childrenUri,
                        new String[] {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        }, null, null, null);

                    if (cursor != null) {
                        try {
                            while (cursor.moveToNext()) {
                                String childDocId = cursor.getString(0);
                                String displayName = cursor.getString(1);
                                if (displayName == null) continue;
                                String lower = displayName.toLowerCase();
                                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".webp")) {
                                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId);
                                    try {
                                        copyUriToTemp(childUri, displayName);
                                    } catch (Exception e) {
                                        Log.w(TAG, "Could not copy texture " + displayName + ": " + e.getMessage());
                                    }
                                }
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not enumerate atlas directory: " + e.getMessage());
        }

        // Final fallback: parse atlas file and build sibling URIs
        try {
            String atlasContent = new String(java.nio.file.Files.readAllBytes(atlasFile.toPath()));
            String[] lines = atlasContent.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.endsWith(".png") || trimmed.endsWith(".jpg") || trimmed.endsWith(".webp")) {
                    if (new File(cacheDir, trimmed).exists()) continue;
                    Uri textureUri = buildSiblingUri(atlasUri, trimmed);
                    if (textureUri != null) {
                        try {
                            copyUriToTemp(textureUri, trimmed);
                        } catch (Exception e) {
                            Log.w(TAG, "Could not copy texture " + trimmed + ": " + e.getMessage());
                        }
                    } else {
                        Log.w(TAG, "Could not build URI for texture: " + trimmed);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse atlas for textures: " + e.getMessage());
        }
    }

    /** Extracts a display filename from a content URI. */
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
        // Fallback: last path segment
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

    /**
     * Extracts the tree URI from a document URI that lives under a tree.
     * E.g. content://authority/tree/{treeId}/document/{docId} →
     *      content://authority/tree/{treeId}
     */
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

    /**
     * Build a URI for a sibling file using DocumentsContract.
     * Note: documentId passed to buildDocumentUriUsingTree should NOT be pre-encoded;
     * the method uses Uri.Builder.appendPath() which encodes automatically.
     */
    protected Uri buildSiblingUri(Uri uri, String siblingName) {
        try {
            String docId = DocumentsContract.getDocumentId(uri);
            if (docId == null) return null;
            Uri treeUri = extractTreeUri(uri);
            if (treeUri == null) return null;
            int lastSep = docId.lastIndexOf('/');
            String parentDocId = lastSep >= 0 ? docId.substring(0, lastSep) : docId;
            // Document IDs use raw (decoded) paths; buildDocumentUriUsingTree will encode
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

    @Override
    public List<String> getAnimations() { return new ArrayList<>(); }
    @Override
    public List<String> getSkins() { return new ArrayList<>(); }
    @Override
    public void setAnimation(String name, boolean loop) { currentAnimation = name; looping = loop; }
    @Override
    public void setSkin(String name) {}
}
