package com.spineviewer.spine.engines;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;

import com.spineviewer.spine.runtime.v20.Animation;
import com.spineviewer.spine.runtime.v20.AnimationState;
import com.spineviewer.spine.runtime.v20.AnimationStateData;
import com.spineviewer.spine.runtime.v20.Skeleton;
import com.spineviewer.spine.runtime.v20.SkeletonData;
import com.spineviewer.spine.runtime.v20.SkeletonJson;
import com.spineviewer.spine.runtime.v20.SkeletonBinary;
import com.spineviewer.spine.runtime.v20.SkeletonRenderer;
import com.spineviewer.spine.runtime.v20.SkeletonRendererDebug;
import com.spineviewer.spine.runtime.v20.Skin;

import com.spineviewer.spine.SpineVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * Spine 2.0 viewer engine.
 * Uses the bundled spine-libgdx-2.0 runtime.
 */
public class SpineEngine20 extends AbstractSpineEngine {

    private TextureAtlas textureAtlas;
    private SkeletonRenderer skeletonRenderer;
    private SkeletonRendererDebug debugRenderer;
    private Skeleton skeleton;
    private AnimationState animationState;
    private final List<String> animationNames = new ArrayList<>();
    private final List<String> skinNames = new ArrayList<>();

    @Override
    protected void loadSkeleton() throws Exception {
        version = SpineVersion.V2_0;

        skeletonRenderer = new SkeletonRenderer();
        skeletonRenderer.setPremultipliedAlpha(false);
        debugRenderer = new SkeletonRendererDebug();

        if (atlasFileHandle == null) {
            throw new Exception("No .atlas file found.\nPlease place the .atlas and .png files in the same folder as the skeleton.");
        }
        textureAtlas = new TextureAtlas(atlasFileHandle);

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

        Array<Animation> anims = skeletonData.getAnimations();
        for (int i = 0; i < anims.size; i++) animationNames.add(anims.get(i).getName());
        Array<Skin> skins = skeletonData.getSkins();
        for (int i = 0; i < skins.size; i++) skinNames.add(skins.get(i).getName());

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
        debugRenderer.getShapeRenderer().setProjectionMatrix(camera.combined);
        debugRenderer.setBones(showBones);
        debugRenderer.draw(skeleton);
    }

    @Override
    public void setAnimation(String name, boolean loop) {
        currentAnimation = name; looping = loop;
        if (animationState != null) animationState.setAnimation(0, name, loop);
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
        Skin combined = new Skin("combined");
        int slotCount = skeleton.getSlots().size;
        com.badlogic.gdx.utils.Array<String> names = new com.badlogic.gdx.utils.Array<>();
        for (int idx = skinNames.size() - 1; idx >= 0; idx--) {
            Skin skin = skeleton.getData().findSkin(skinNames.get(idx));
            if (skin == null) continue;
            for (int i = 0; i < slotCount; i++) {
                names.clear();
                skin.findNamesForSlot(i, names);
                for (int j = 0; j < names.size; j++) {
                    com.spineviewer.spine.runtime.v20.attachments.Attachment att = skin.getAttachment(i, names.get(j));
                    if (att != null) combined.addAttachment(i, names.get(j), att);
                }
            }
        }
        skeleton.setSkin(combined);
        skeleton.setSlotsToSetupPose();
    }

    @Override public List<String> getAnimations() { return animationNames; }
    @Override public List<String> getSkins() { return skinNames; }

    @Override
    public void dispose() {
        super.dispose();
        if (textureAtlas != null) textureAtlas.dispose();
    }
}
