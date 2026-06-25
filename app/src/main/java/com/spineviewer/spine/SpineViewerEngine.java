package com.spineviewer.spine;

import android.content.Context;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;

/**
 * Base interface for spine viewer engines, one per runtime version.
 * Each version implementation knows how to load skeleton data and render it
 * using its specific spine runtime classes.
 */
public abstract class SpineViewerEngine implements ApplicationListener {
    private static final String TAG = "SpineViewerEngine";

    protected Context context;
    protected Uri skeletonUri;
    protected Uri atlasUri;
    protected SpineVersion version;
    protected java.util.List<Uri> textureUris;

    // State
    protected boolean loaded = false;
    protected String loadError = null;
    protected String currentAnimation = null;
    protected boolean looping = true;
    protected boolean paused = false;
    protected float timeScale = 1.0f;
    protected boolean showBones = false;
    protected boolean showRegions = false;

    // Camera
    protected OrthographicCamera camera;
    protected PolygonSpriteBatch batch;

    // Touch/gesture for pan+zoom
    protected float camX, camY, camZoom = 1.0f;
    protected float skeletonX, skeletonY;

    // Listener for state updates to the UI
    public interface StateListener {
        void onLoaded(java.util.List<String> animations, java.util.List<String> skins, SpineVersion version);
        void onError(String message);
        void onAnimationComplete(String animationName);
    }
    protected StateListener stateListener;

    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    public void init(Context context, Uri skeletonUri, Uri atlasUri, SpineVersion version) {
        init(context, skeletonUri, atlasUri, version, null);
    }

    public void init(Context context, Uri skeletonUri, Uri atlasUri, SpineVersion version,
                     java.util.List<Uri> textureUris) {
        this.context = context;
        this.skeletonUri = skeletonUri;
        this.atlasUri = atlasUri;
        this.version = version;
        this.textureUris = textureUris;
    }

    public boolean isLoaded() { return loaded; }
    public String getLoadError() { return loadError; }
    public String getCurrentAnimation() { return currentAnimation; }
    public boolean isLooping() { return looping; }
    public float getTimeScale() { return timeScale; }

    public void setTimeScale(float scale) { this.timeScale = scale; }
    public void setLooping(boolean loop) { this.looping = loop; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean isPaused() { return paused; }
    public void setShowBones(boolean show) { this.showBones = show; }
    public void setShowRegions(boolean show) { this.showRegions = show; }

    /** Returns the ShapeRenderer used for debug drawing, or null if unavailable. */
    public com.badlogic.gdx.graphics.glutils.ShapeRenderer getDebugShapeRenderer() { return null; }

    public abstract void setAnimation(String name, boolean loop);
    public abstract void setSkin(String name);
    /** Set multiple skins simultaneously (layered/combined). */
    public void setSkins(java.util.List<String> skinNames) {
        if (skinNames == null || skinNames.isEmpty()) return;
        setSkin(skinNames.get(0)); // default: just use first skin
    }
    public abstract java.util.List<String> getAnimations();
    public abstract java.util.List<String> getSkins();

    protected void notifyLoaded(java.util.List<String> animations, java.util.List<String> skins) {
        if (stateListener != null) {
            Gdx.app.postRunnable(() ->
                stateListener.onLoaded(animations, skins, version)
            );
        }
    }

    protected void notifyError(String msg) {
        loadError = msg;
        Log.e(TAG, "SpineEngine error: " + msg);
        if (stateListener != null) {
            Gdx.app.postRunnable(() -> stateListener.onError(msg));
        }
    }

    /**
     * Handle pinch-to-zoom gesture
     */
    public void onZoom(float scaleFactor) {
        camZoom /= scaleFactor;
        camZoom = MathUtils.clamp(camZoom, 0.1f, 10f);
        if (camera != null) camera.zoom = camZoom;
    }

    /**
     * Handle pan gesture
     */
    public void onPan(float dx, float dy) {
        camX -= dx * camZoom;
        camY += dy * camZoom;
        if (camera != null) {
            camera.position.set(camX, camY, 0);
        }
    }

    public void resetCamera() {
        camZoom = 1.0f;
        camX = skeletonX;
        camY = skeletonY;
        if (camera != null) {
            camera.zoom = camZoom;
            camera.position.set(camX, camY, 0);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.setToOrtho(false, width, height);
            camera.position.set(camX, camY, 0);
            camera.zoom = camZoom;
        }
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
    }
}
