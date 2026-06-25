package com.spineviewer.spine.engines;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;

import com.spineviewer.spine.runtime.v40.Animation;
import com.spineviewer.spine.runtime.v40.AnimationState;
import com.spineviewer.spine.runtime.v40.AnimationStateData;
import com.spineviewer.spine.runtime.v40.Skeleton;
import com.spineviewer.spine.runtime.v40.SkeletonData;
import com.spineviewer.spine.runtime.v40.SkeletonJson;
import com.spineviewer.spine.runtime.v40.SkeletonBinary;
import com.spineviewer.spine.runtime.v40.SkeletonRenderer;
import com.spineviewer.spine.runtime.v40.SkeletonRendererDebug;
import com.spineviewer.spine.runtime.v40.Skin;

import com.spineviewer.spine.SpineVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * Spine 4.0 viewer engine.
 * Uses the bundled spine-libgdx-4.0 runtime.
 */
public class SpineEngine40 extends AbstractSpineEngine {

    private TextureAtlas textureAtlas;
    private SkeletonRenderer skeletonRenderer;
    private SkeletonRendererDebug debugRenderer;
    private Skeleton skeleton;
    private AnimationState animationState;
    private final List<String> animationNames = new ArrayList<>();
    private final List<String> skinNames = new ArrayList<>();

    @Override
    protected void loadSkeleton() throws Exception {
        version = SpineVersion.V4_0;

        skeletonRenderer = new SkeletonRenderer();
        skeletonRenderer.setPremultipliedAlpha(false);
        debugRenderer = new SkeletonRendererDebug();

        if (atlasFileHandle == null) {
            throw new Exception("No .atlas file found.\nPlease place the .atlas and .png texture files in the same folder as the skeleton file.");
        }
        textureAtlas = new TextureAtlas(atlasFileHandle);

        // Determine format and load skeleton data
        SkeletonData skeletonData;
        String fname = skeletonFileHandle.name().toLowerCase();
        if (fname.endsWith(".skel") || fname.endsWith(".bytes")) {
            SkeletonBinary binary = new SkeletonBinary(textureAtlas);
            binary.setScale(1.0f);
            skeletonData = binary.readSkeletonData(skeletonFileHandle);
        } else {
            SkeletonJson json = new SkeletonJson(textureAtlas);
            json.setScale(1.0f);
            skeletonData = json.readSkeletonData(skeletonFileHandle);
        }

        // Collect animation names
        Array<Animation> anims = skeletonData.getAnimations();
        for (int i = 0; i < anims.size; i++) {
            animationNames.add(anims.get(i).getName());
        }

        // Collect skin names
        Array<Skin> skins = skeletonData.getSkins();
        for (int i = 0; i < skins.size; i++) {
            skinNames.add(skins.get(i).getName());
        }

        // Build skeleton and center on screen
        skeleton = new Skeleton(skeletonData);
        skeleton.setToSetupPose();
        skeleton.updateWorldTransform();
        skeletonX = Gdx.graphics.getWidth() / 2f;
        skeletonY = Gdx.graphics.getHeight() / 3f;
        skeleton.setX(skeletonX);
        skeleton.setY(skeletonY);
        camX = skeletonX;
        camY = skeletonY;
        if (camera != null) camera.position.set(camX, camY, 0);

        // Set up animation state
        AnimationStateData stateData = new AnimationStateData(skeletonData);
        animationState = new AnimationState(stateData);
        if (!animationNames.isEmpty()) {
            currentAnimation = animationNames.get(0);
            animationState.setAnimation(0, currentAnimation, true);
        }

        loaded = true;
        notifyLoaded(animationNames, skinNames);
    }

    @Override
    protected void renderSkeleton(float delta) {
        if (!loaded || skeleton == null) return;
        animationState.update(delta * timeScale);
        animationState.apply(skeleton);
        skeleton.updateWorldTransform();
        batch.begin();
        skeletonRenderer.draw(batch, skeleton);
        batch.end();
    }

    @Override
    protected void renderDebug() {
        if (skeleton == null || debugRenderer == null) return;
        debugRenderer.setBones(showBones);
        debugRenderer.getShapeRenderer().setProjectionMatrix(camera.combined);
        debugRenderer.draw(skeleton);
    }

    @Override
    public void setAnimation(String name, boolean loop) {
        currentAnimation = name;
        looping = loop;
        if (animationState != null) {
            animationState.setAnimation(0, name, loop);
        }
    }

    @Override
    public void setSkin(String name) {
        if (skeleton != null) {
            skeleton.setSkin(name);
            skeleton.setSlotsToSetupPose();
        }
    }

    @Override
    public void setSkins(List<String> skinNames) {
        if (skeleton == null || skinNames == null || skinNames.isEmpty()) return;
        if (skinNames.size() == 1) { setSkin(skinNames.get(0)); return; }
        // Build combined skin in reverse order: last selected first for priority
        Skin combined = new Skin("combined");
        for (int i = skinNames.size() - 1; i >= 0; i--) {
            Skin skin = skeleton.getData().findSkin(skinNames.get(i));
            if (skin != null) combined.addSkin(skin);
        }
        skeleton.setSkin(combined);
        // For each slot, use the best attachment from the combined skin
        var slots = skeleton.getSlots();
        for (int i = 0, n = slots.size; i < n; i++) {
            boolean found = false;
            for (var entry : combined.getAttachments()) {
                if (entry.getSlotIndex() == i) {
                    slots.get(i).setAttachment(entry.getAttachment());
                    found = true;
                    break;
                }
            }
            if (!found) slots.get(i).setAttachment(null);
        }
    }

    @Override
    public List<String> getAnimations() { return animationNames; }

    @Override
    public List<String> getSkins() { return skinNames; }

    @Override
    public void dispose() {
        super.dispose();
        if (textureAtlas != null) textureAtlas.dispose();
    }
}
