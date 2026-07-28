package com.spineviewer.ui;

import android.app.ProgressDialog;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.spineviewer.R;
import com.spineviewer.spine.SpineEngineFactory;
import com.spineviewer.spine.SpineVersion;
import com.spineviewer.spine.SpineViewerEngine;
import com.spineviewer.utils.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpinePreviewActivity extends AndroidApplication
        implements SpineViewerEngine.StateListener {

    public static final String EXTRA_SKELETON_URI = "skeleton_uri";
    public static final String EXTRA_ATLAS_URI    = "atlas_uri";
    public static final String EXTRA_VERSION      = "version";
    public static final String EXTRA_NAME         = "name";
    public static final String EXTRA_TEXTURE_URIS = "texture_uris";

    private SpineViewerEngine engine;
    private PreferenceManager prefManager;

    private View controlPanel;
    private Spinner spinnerAnimation;
    private android.widget.Button btnSkin;
    private SeekBar seekTimeScale;
    private TextView tvTimeScale, tvStatus, tvVersion;
    private Switch switchPremultiply;
    private ImageButton btnTogglePanel, btnResetCamera, btnShowBones;
    private ImageButton btnPause, btnPrev, btnNext, btnChangeVersion;
    private Button btnExportFrames;

    private ScaleGestureDetector scaleDetector;
    private float lastTouchX, lastTouchY;
    private boolean panelVisible = true;
    private boolean showBones = false;

    private SpineVersion currentVersion;
    private String skeletonUriStr, atlasUriStr, skeletonName;
    private List<String> animations;
    private List<String> skins;
    private int currentAnimIdx = 0;
    private boolean[] selectedSkins;
    private ArrayList<Uri> textureUris;

    private boolean isExporting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spine_preview);

        prefManager = new PreferenceManager(this);

        skeletonUriStr = getIntent().getStringExtra(EXTRA_SKELETON_URI);
        atlasUriStr    = getIntent().getStringExtra(EXTRA_ATLAS_URI);
        String versionName = getIntent().getStringExtra(EXTRA_VERSION);
        skeletonName   = getIntent().getStringExtra(EXTRA_NAME);
        currentVersion = versionName != null ? SpineVersion.valueOf(versionName) : SpineVersion.latest();
        textureUris    = getParcelableUriListCompat(getIntent(), EXTRA_TEXTURE_URIS);

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
        switchPremultiply= findViewById(R.id.switch_premultiply);
        btnTogglePanel   = findViewById(R.id.btn_toggle_panel);
        btnResetCamera   = findViewById(R.id.btn_reset_camera);
        btnShowBones     = findViewById(R.id.btn_show_bones);
        btnPause         = findViewById(R.id.btn_pause);
        btnPrev          = findViewById(R.id.btn_prev_anim);
        btnNext          = findViewById(R.id.btn_next_anim);
        btnChangeVersion = findViewById(R.id.btn_change_version);
        btnExportFrames  = findViewById(R.id.btn_export_frames);

        tvVersion.setText(getString(R.string.version_prefix) + currentVersion.getDisplayName());
        tvStatus.setText(R.string.loading);

        seekTimeScale.setMax(19);
        seekTimeScale.setProgress(9);
        tvTimeScale.setText(R.string.default_speed);
        seekTimeScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                float scale = (p + 1) / 10f;
                tvTimeScale.setText(String.format("%.1f×", scale));
                if (engine != null) engine.setTimeScale(scale);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        if (engine != null) engine.setLooping(true);

        switchPremultiply.setChecked(prefManager.getDefaultPremultiplyAlpha());
        switchPremultiply.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (engine != null) engine.setPremultipliedAlpha(isChecked);
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

        btnExportFrames.setOnClickListener(v -> showExportDialog());

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

        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        if (engine != null) engine.onZoom(d.getScaleFactor());
                        return true;
                    }
                });
    }

    private void showExportDialog() {
        if (animations == null || animations.isEmpty()) {
            Toast.makeText(this, "没有可导出的动画", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("导出帧序列");

        View view = getLayoutInflater().inflate(R.layout.dialog_export_frames, null);
        EditText etAnimationName = view.findViewById(R.id.et_export_animation_name);
        EditText etStartFrame = view.findViewById(R.id.et_export_start_frame);
        EditText etEndFrame = view.findViewById(R.id.et_export_end_frame);
        EditText etFps = view.findViewById(R.id.et_export_fps);

        etAnimationName.setText(spinnerAnimation.getSelectedItem().toString());
        etStartFrame.setText("0");
        etEndFrame.setText("100");
        etFps.setText("30");

        builder.setView(view);
        builder.setPositiveButton("导出", (dialog, which) -> {
            String animName = etAnimationName.getText().toString().trim();
            if (animName.isEmpty()) {
                Toast.makeText(this, "请输入动画名称", Toast.LENGTH_SHORT).show();
                return;
            }
            int startFrame, endFrame, fps;
            try {
                startFrame = Integer.parseInt(etStartFrame.getText().toString().trim());
                endFrame = Integer.parseInt(etEndFrame.getText().toString().trim());
                fps = Integer.parseInt(etFps.getText().toString().trim());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                return;
            }
            if (startFrame < 0) startFrame = 0;
            if (endFrame <= startFrame) endFrame = startFrame + 1;
            if (fps < 1) fps = 1;
            if (fps > 60) fps = 60;

            startExport(animName, startFrame, endFrame, fps);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void startExport(String animName, int startFrame, int endFrame, int fps) {
        if (isExporting) {
            Toast.makeText(this, "正在导出中", Toast.LENGTH_SHORT).show();
            return;
        }
        if (engine == null || !engine.isLoaded()) {
            Toast.makeText(this, "引擎未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        engine.setPaused(true);
        btnPause.setImageResource(android.R.drawable.ic_media_play);

        new ExportTask().execute(animName, startFrame, endFrame, fps);
    }

    private class ExportTask extends AsyncTask<Object, Integer, String> {

        private ProgressDialog progressDialog;
        private int totalFrames;

        @Override
        protected void onPreExecute() {
            isExporting = true;
            progressDialog = new ProgressDialog(SpinePreviewActivity.this);
            progressDialog.setMessage("正在导出帧...");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(Object... params) {
            String animName = (String) params[0];
            int startFrame = (int) params[1];
            int endFrame = (int) params[2];
            int fps = (int) params[3];

            totalFrames = endFrame - startFrame + 1;
            publishProgress(0);

            String storagePath = prefManager.getStorageDirectoryUri();
            File outputDir;

            if (storagePath != null) {
                try {
                    Uri uri = Uri.parse(storagePath);
                    DocumentFile doc = DocumentFile.fromTreeUri(SpinePreviewActivity.this, uri);
                    if (doc != null && doc.exists()) {
                        String docPath = doc.getUri().getPath();
                        if (docPath != null && docPath.startsWith("/tree/primary:")) {
                            String relative = docPath.substring(13);
                            File externalDir = Environment.getExternalStorageDirectory();
                            outputDir = new File(externalDir, relative);
                            if (!outputDir.exists()) {
                                outputDir.mkdirs();
                            }
                        } else {
                            outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        }
                    } else {
                        outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    }
                } catch (Exception e) {
                    outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                }
            } else {
                outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String folderName = animName + "_" + timestamp;
            File frameDir = new File(outputDir, folderName);
            if (!frameDir.exists()) {
                frameDir.mkdirs();
            }

            float frameStep = 1f / fps;
            float duration = engine.getAnimationDuration();
            if (duration <= 0) {
                return "ERROR:无法获取动画时长";
            }

            float totalTime = duration;
            float currentTime = startFrame * frameStep;
            float maxTime = endFrame * frameStep;
            if (maxTime > totalTime) {
                maxTime = totalTime;
                totalFrames = (int) ((maxTime - currentTime) / frameStep) + 1;
            }

            int frameIndex = 0;
            while (currentTime <= maxTime && !isCancelled()) {
                engine.setAnimationPosition(currentTime / totalTime);
                Gdx.gl.glFinish();

                byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0,
                        Gdx.graphics.getBackBufferWidth(),
                        Gdx.graphics.getBackBufferHeight(), true);

                Pixmap pixmap = new Pixmap(
                        Gdx.graphics.getBackBufferWidth(),
                        Gdx.graphics.getBackBufferHeight(),
                        Pixmap.Format.RGBA8888);
                pixmap.getPixels().put(pixels);
                pixmap.getPixels().position(0);

                String fileName = String.format(Locale.getDefault(), "frame_%04d.png", frameIndex);
                File frameFile = new File(frameDir, fileName);

                PixmapIO.writePNG(frameFile, pixmap);
                pixmap.dispose();

                frameIndex++;
                currentTime += frameStep;
                int progress = (int) ((float) frameIndex / totalFrames * 100);
                publishProgress(Math.min(progress, 100));
            }

            engine.setAnimation(animations.get(currentAnimIdx), true);
            engine.setPaused(false);
            runOnUiThread(() -> btnPause.setImageResource(android.R.drawable.ic_media_pause));

            return "SUCCESS:" + frameDir.getAbsolutePath() + ":" + frameIndex;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (progressDialog != null) {
                progressDialog.setProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            isExporting = false;
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            if (result == null) {
                Toast.makeText(SpinePreviewActivity.this, "导出失败", Toast.LENGTH_SHORT).show();
                return;
            }

            if (result.startsWith("ERROR:")) {
                Toast.makeText(SpinePreviewActivity.this,
                        getString(R.string.export_failed, result.substring(6)),
                        Toast.LENGTH_LONG).show();
            } else if (result.startsWith("SUCCESS:")) {
                String[] parts = result.split(":");
                String path = parts[1];
                int count = Integer.parseInt(parts[2]);
                Toast.makeText(SpinePreviewActivity.this,
                        "导出完成，共 " + count + " 帧\n保存位置：" + path,
                        Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onCancelled() {
            isExporting = false;
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            engine.setPaused(false);
            runOnUiThread(() -> btnPause.setImageResource(android.R.drawable.ic_media_pause));
        }
    }

    private void launchEngine(SpineVersion version) {
        FrameLayout container = findViewById(R.id.gl_container);
        container.removeAllViews();

        currentVersion = version;
        tvVersion.setText(getString(R.string.version_prefix) + version.getDisplayName());
        tvStatus.setText(R.string.loading);

        engine = SpineEngineFactory.create(version);
        engine.init(this,
                Uri.parse(skeletonUriStr),
                atlasUriStr != null ? Uri.parse(atlasUriStr) : null,
                version,
                textureUris);
        engine.setStateListener(this);
        engine.setLooping(true);

        boolean defaultPremultiply = prefManager.getDefaultPremultiplyAlpha();
        engine.setPremultipliedAlpha(defaultPremultiply);
        switchPremultiply.setChecked(defaultPremultiply);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useGL30 = false;
        config.numSamples = 2;
        config.disableAudio = true;

        View glView = initializeForView(engine, config);
        container.addView(glView, 0,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

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
        if (engine != null) {
            engine.setAnimation(animations.get(currentAnimIdx), true);
        }
    }

    private void showVersionPicker() {
        SpineVersion[] versions = SpineVersion.values();
        String[] labels = new String[versions.length];
        int sel = 0;
        for (int i = 0; i < versions.length; i++) {
            labels[i] = getString(R.string.version_item, versions[i].getDisplayName());
            if (versions[i] == currentVersion) sel = i;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.switch_version_title)
                .setSingleChoiceItems(labels, sel, (d, which) -> {
                    d.dismiss();
                    if (versions[which] != currentVersion) {
                        launchEngine(versions[which]);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .create();
        dialog.show();
        android.widget.Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    @Override
    public void onLoaded(List<String> animations, List<String> skins, SpineVersion version) {
        this.animations = animations;
        this.skins = skins;
        this.currentVersion = version;
        selectedSkins = new boolean[skins.size()];
        if (!skins.isEmpty()) selectedSkins[0] = true;

        runOnUiThread(() -> {
            tvStatus.setText(getString(R.string.status_loaded, animations.size(), skins.size()));
            tvVersion.setText(getString(R.string.version_prefix) + version.getDisplayName());

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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.skin_picker_title)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton(R.string.skin_apply, (d, which) -> {
                    boolean anySelected = false;
                    for (boolean b : checked) if (b) { anySelected = true; break; }
                    if (!anySelected && checked.length > 0) checked[0] = true;
                    selectedSkins = checked;
                    updateSkinButton();
                    applySelectedSkins();
                })
                .setNegativeButton(R.string.skin_cancel, null)
                .create();
        dialog.show();
        android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.accent));
        }
        android.widget.Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void updateSkinButton() {
        if (btnSkin == null || skins == null) return;
        List<String> active = new ArrayList<>();
        for (int i = 0; i < skins.size(); i++) {
            if (i < selectedSkins.length && selectedSkins[i]) active.add(skins.get(i));
        }
        if (active.isEmpty()) {
            btnSkin.setText(R.string.skin_none);
        } else if (active.size() == 1) {
            btnSkin.setText(active.get(0));
        } else {
            btnSkin.setText(getString(R.string.skin_multiple, active.size()));
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
            tvStatus.setText(getString(R.string.error_loading, message));
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.load_error) + " — Spine " + currentVersion.getDisplayName())
                    .setMessage(message + "\n\n" + getString(R.string.try_switch_version))
                    .setPositiveButton(R.string.switch_version, (d, w) -> showVersionPicker())
                    .setNegativeButton(R.string.close, (d, w) -> finish())
                    .create();
            dialog.show();
            android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.accent));
            }
            android.widget.Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary));
            }
        });
    }

    @Override
    public void onAnimationComplete(String animationName) {}

    @SuppressWarnings({"deprecation", "unchecked"})
    private static ArrayList<Uri> getParcelableUriListCompat(android.content.Intent intent, String key) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableArrayListExtra(key, Uri.class);
        } else {
            return intent.getParcelableArrayListExtra(key);
        }
    }
}
