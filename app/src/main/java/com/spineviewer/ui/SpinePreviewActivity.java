package com.spineviewer.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.spineviewer.R;
import com.spineviewer.spine.SpineEngineFactory;
import com.spineviewer.spine.SpineVersion;
import com.spineviewer.spine.SpineViewerEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Full-screen Spine animation preview.
 * Extends AndroidApplication to host the libGDX GL view.
 */
public class SpinePreviewActivity extends AndroidApplication
        implements SpineViewerEngine.StateListener {

    public static final String EXTRA_SKELETON_URI = "skeleton_uri";
    public static final String EXTRA_ATLAS_URI    = "atlas_uri";
    public static final String EXTRA_VERSION      = "version";
    public static final String EXTRA_NAME         = "name";
    public static final String EXTRA_TEXTURE_URIS = "texture_uris";

    private SpineViewerEngine engine;

    // UI
    private View controlPanel;
    private Spinner spinnerAnimation;
    private android.widget.Button btnSkin;
    private SeekBar seekTimeScale;
    private TextView tvTimeScale, tvStatus, tvVersion;
    private ImageButton btnTogglePanel, btnResetCamera, btnShowBones;
    private ImageButton btnPause, btnPrev, btnNext, btnChangeVersion;

    // Touch
    private ScaleGestureDetector scaleDetector;
    private float lastTouchX, lastTouchY;
    private boolean panelVisible = true;
    private boolean showBones = false;

    // State
    private SpineVersion currentVersion;
    private String skeletonUriStr, atlasUriStr, skeletonName;
    private List<String> animations;
    private List<String> skins;
    private int currentAnimIdx = 0;
    private boolean[] selectedSkins;
    private ArrayList<Uri> textureUris;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set our custom layout (which includes the GL container + UI overlay)
        setContentView(R.layout.activity_spine_preview);

        skeletonUriStr = getIntent().getStringExtra(EXTRA_SKELETON_URI);
        atlasUriStr    = getIntent().getStringExtra(EXTRA_ATLAS_URI);
        String versionName = getIntent().getStringExtra(EXTRA_VERSION);
        skeletonName   = getIntent().getStringExtra(EXTRA_NAME);
        currentVersion = versionName != null ? SpineVersion.valueOf(versionName) : SpineVersion.latest();
        textureUris    = 
        getParcelableUriListCompat(getIntent(), EXTRA_TEXTURE_URIS);

        bindViews();
        launchEngine(currentVersion);
    }

    private void bindViews() {
        controlPanel     = findViewById(R.id.control_panel);
        spinnerAnimation = findViewById(R.id.spinner_animation);
        btnSkin          = findViewById(R.id.spinner_skin);
        seekTimeScale    = findViewById(R.id.seek_time_scale);
        tvTimeScale      = findViewById(R.id.tv_time_scale);
        tvStatus         = findViewById(R.id.tv_status);
        tvVersion        = findViewById(R.id.tv_version_badge);
        btnTogglePanel   = findViewById(R.id.btn_toggle_panel);
        btnResetCamera   = findViewById(R.id.btn_reset_camera);
        btnShowBones     = findViewById(R.id.btn_show_bones);
        btnPause         = findViewById(R.id.btn_pause);
        btnPrev          = findViewById(R.id.btn_prev_anim);
        btnNext          = findViewById(R.id.btn_next_anim);
        btnChangeVersion = findViewById(R.id.btn_change_version);

        tvVersion.setText("v" + currentVersion.getDisplayName());
        tvStatus.setText("Loading…");

        seekTimeScale.setMax(19);
        seekTimeScale.setProgress(9);
        tvTimeScale.setText("1.0×");
        seekTimeScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                float scale = (p + 1) / 10f;
                tvTimeScale.setText(String.format("%.1f×", scale));
                if (engine != null) engine.setTimeScale(scale);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnTogglePanel.setOnClickListener(v -> {
            panelVisible = !panelVisible;
            controlPanel.setVisibility(panelVisible ? View.VISIBLE : View.GONE);
        });

        btnResetCamera.setOnClickListener(v -> { if (engine != null) engine.resetCamera(); });

        btnShowBones.setOnClickListener(v -> {
            showBones = !showBones;
            if (engine != null) engine.setShowBones(showBones);
            btnShowBones.setAlpha(showBones ? 1.0f : 0.4f);
        });

        btnPause.setOnClickListener(v -> {
            if (engine != null) {
                boolean newPaused = !engine.isPaused();
                engine.setPaused(newPaused);
                btnPause.setImageResource(newPaused
                    ? android.R.drawable.ic_media_play
                    : android.R.drawable.ic_media_pause);
            }
        });

        btnPrev.setOnClickListener(v -> stepAnimation(-1));
        btnNext.setOnClickListener(v -> stepAnimation(1));

        btnChangeVersion.setOnClickListener(v -> showVersionPicker());

        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        spinnerAnimation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                currentAnimIdx = pos;
                if (animations != null && engine != null) {
                    engine.setAnimation(animations.get(pos), true);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btnSkin.setOnClickListener(v -> showSkinPicker());

        // Pinch-to-zoom
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        if (engine != null) engine.onZoom(d.getScaleFactor());
                        return true;
                    }
                });
    }

    private void launchEngine(SpineVersion version) {
        // Remove old GL view if re-launching
        FrameLayout container = findViewById(R.id.gl_container);
        container.removeAllViews();

        currentVersion = version;
        tvVersion.setText("v" + version.getDisplayName());
        tvStatus.setText("Loading…");

        engine = SpineEngineFactory.create(version);
        engine.init(this,
                Uri.parse(skeletonUriStr),
                atlasUriStr != null ? Uri.parse(atlasUriStr) : null,
                version,
                textureUris);
        engine.setStateListener(this);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useGL30 = false;
        config.numSamples = 2;
        config.disableAudio = true;

        // initializeForView creates the GL surface; we embed it in our container
        View glView = initializeForView(engine, config);
        container.addView(glView, 0,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        // Touch on GL view
        glView.setOnTouchListener(this::onGlTouch);
    }

    private boolean onGlTouch(View v, MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (!scaleDetector.isInProgress()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 1 && engine != null) {
                        engine.onPan(event.getX() - lastTouchX, event.getY() - lastTouchY);
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    break;
            }
        }
        return true;
    }

    private void stepAnimation(int dir) {
        if (animations == null || animations.isEmpty()) return;
        currentAnimIdx = (currentAnimIdx + dir + animations.size()) % animations.size();
        spinnerAnimation.setSelection(currentAnimIdx);
        if (engine != null) engine.setAnimation(animations.get(currentAnimIdx), true);
    }

    private void showVersionPicker() {
        SpineVersion[] versions = SpineVersion.values();
        String[] labels = new String[versions.length];
        int sel = 0;
        for (int i = 0; i < versions.length; i++) {
            labels[i] = "Spine " + versions[i].getDisplayName();
            if (versions[i] == currentVersion) sel = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Switch Runtime Version")
                .setSingleChoiceItems(labels, sel, (dialog, which) -> {
                    dialog.dismiss();
                    if (versions[which] != currentVersion) {
                        launchEngine(versions[which]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── StateListener ──────────────────────────────────────────────────────

    @Override
    public void onLoaded(List<String> animations, List<String> skins, SpineVersion version) {
        this.animations = animations;
        this.skins = skins;
        this.currentVersion = version;
        // Initialize: select the first skin by default
        selectedSkins = new boolean[skins.size()];
        if (!skins.isEmpty()) selectedSkins[0] = true;

        runOnUiThread(() -> {
            tvStatus.setText("✓  " + animations.size() + " animations  •  "
                    + skins.size() + " skins");
            tvVersion.setText("v" + version.getDisplayName());

            ArrayAdapter<String> animAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, animations);
            animAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAnimation.setAdapter(animAdapter);

            updateSkinButton();
            applySelectedSkins();
        });
    }

    private void showSkinPicker() {
        if (skins == null || skins.isEmpty()) return;
        String[] labels = skins.toArray(new String[0]);
        boolean[] checked = new boolean[skins.size()];
        for (int i = 0; i < skins.size(); i++) checked[i] = selectedSkins[i];

        new AlertDialog.Builder(this)
                .setTitle("Select Skins (multi-select)")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton("Apply", (dialog, which) -> {
                    // Ensure at least one skin is selected
                    boolean anySelected = false;
                    for (boolean b : checked) if (b) { anySelected = true; break; }
                    if (!anySelected && checked.length > 0) checked[0] = true;
                    selectedSkins = checked;
                    updateSkinButton();
                    applySelectedSkins();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSkinButton() {
        if (btnSkin == null || skins == null) return;
        List<String> active = new ArrayList<>();
        for (int i = 0; i < skins.size(); i++) {
            if (i < selectedSkins.length && selectedSkins[i]) active.add(skins.get(i));
        }
        if (active.isEmpty()) {
            btnSkin.setText("none");
        } else if (active.size() == 1) {
            btnSkin.setText(active.get(0));
        } else {
            btnSkin.setText(active.size() + " skins selected");
        }
    }

    private void applySelectedSkins() {
        if (engine == null || skins == null) return;
        List<String> active = new ArrayList<>();
        for (int i = 0; i < skins.size(); i++) {
            if (i < selectedSkins.length && selectedSkins[i]) active.add(skins.get(i));
        }
        if (!active.isEmpty()) {
            engine.setSkins(active);
        }
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            tvStatus.setText("⚠ " + message);
            new AlertDialog.Builder(this)
                    .setTitle("Load Error — Spine " + currentVersion.getDisplayName())
                    .setMessage(message + "\n\nTry a different runtime version?")
                    .setPositiveButton("Switch Version", (d, w) -> showVersionPicker())
                    .setNegativeButton("Close", (d, w) -> finish())
                    .show();
        });
    }

    @Override
    public void onAnimationComplete(String animationName) {}
    /** Compat helper: safe on API 24+ and API 33+. */
    @SuppressWarnings({"deprecation", "unchecked"})
    private static ArrayList<Uri> getParcelableUriListCompat(android.content.Intent intent, String key) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableArrayListExtra(key, Uri.class);
        } else {
            return intent.getParcelableArrayListExtra(key);
        }
    }

}