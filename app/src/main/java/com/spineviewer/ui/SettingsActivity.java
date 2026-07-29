package com.spineviewer.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.spineviewer.R;
import com.spineviewer.utils.CacheManager;
import com.spineviewer.utils.PreferenceManager;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_FOLDER = 1001;

    private PreferenceManager prefManager;
    private CacheManager cacheManager;
    private TextView tvDefaultFolder;
    private Button btnSelectFolder;
    private Button btnClearFolder;
    private Switch switchDefaultPremultiply;
    private Switch switchDefaultShowBones;
    private Switch switchScanSubdirs;
    private SeekBar seekDefaultSpeed;
    private TextView tvDefaultSpeed;
    private TextView tvCacheSize;
    private Button btnClearCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefManager = new PreferenceManager(this);
        cacheManager = new CacheManager(this);

        tvDefaultFolder = findViewById(R.id.tv_default_folder);
        btnSelectFolder = findViewById(R.id.btn_select_folder);
        btnClearFolder = findViewById(R.id.btn_clear_folder);
        switchDefaultPremultiply = findViewById(R.id.switch_default_premultiply);
        switchDefaultShowBones = findViewById(R.id.switch_default_show_bones);
        switchScanSubdirs = findViewById(R.id.switch_scan_subdirs);
        seekDefaultSpeed = findViewById(R.id.seek_default_speed);
        tvDefaultSpeed = findViewById(R.id.tv_default_speed);
        tvCacheSize = findViewById(R.id.tv_cache_size);
        btnClearCache = findViewById(R.id.btn_clear_cache);

        switchDefaultPremultiply.setChecked(prefManager.getDefaultPremultiplyAlpha());
        switchDefaultShowBones.setChecked(prefManager.getDefaultShowBones());
        switchScanSubdirs.setChecked(prefManager.getScanSubdirectories());

        float currentSpeed = prefManager.getDefaultAnimationSpeed();
        int progress = Math.round(currentSpeed * 10) - 1;
        if (progress < 0) progress = 0;
        if (progress > 19) progress = 19;
        seekDefaultSpeed.setProgress(progress);
        tvDefaultSpeed.setText(String.format("%.1f×", currentSpeed));

        switchDefaultPremultiply.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefManager.setDefaultPremultiplyAlpha(isChecked);
        });

        switchDefaultShowBones.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefManager.setDefaultShowBones(isChecked);
        });

        switchScanSubdirs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefManager.setScanSubdirectories(isChecked);
        });

        seekDefaultSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = (progress + 1) / 10f;
                tvDefaultSpeed.setText(String.format("%.1f×", speed));
                if (fromUser) {
                    prefManager.setDefaultAnimationSpeed(speed);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSelectFolder.setOnClickListener(v -> openFolderPicker());
        btnClearFolder.setOnClickListener(v -> {
            prefManager.saveDefaultFolderUri(null);
            updateDisplay();
            Toast.makeText(this, R.string.default_folder_cleared, Toast.LENGTH_SHORT).show();
        });

        btnClearCache.setOnClickListener(v -> {
            cacheManager.clearAllCache();
            prefManager.clearCacheIndex();
            updateCacheSize();
            Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
        });

        updateDisplay();
        updateCacheSize();
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String uriString = uri.toString();
                prefManager.saveDefaultFolderUri(uriString);
                prefManager.saveLastScanUri(uriString);
                updateDisplay();
                Toast.makeText(this, R.string.default_folder_updated, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getReadablePath(Uri uri) {
        try {
            String path = uri.getPath();
            if (path == null) return null;
            if (path.startsWith("/tree/")) {
                String encoded = path.substring(6);
                String decoded = java.net.URLDecoder.decode(encoded, "UTF-8");
                if (decoded.startsWith("primary:")) {
                    String relative = decoded.substring(8);
                    String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
                    if (relative.isEmpty()) {
                        return extStorage;
                    } else {
                        return extStorage + "/" + relative;
                    }
                } else {
                    return decoded;
                }
            } else {
                return uri.getPath();
            }
        } catch (Exception e) {
            return uri.getPath();
        }
    }

    private void updateDisplay() {
        String uriString = prefManager.getDefaultFolderUri();
        if (uriString != null) {
            try {
                Uri uri = Uri.parse(uriString);
                String readable = getReadablePath(uri);
                if (readable != null && !readable.isEmpty()) {
                    tvDefaultFolder.setText(getString(R.string.current_folder, readable));
                } else {
                    tvDefaultFolder.setText(getString(R.string.current_folder, uriString));
                }
            } catch (Exception e) {
                tvDefaultFolder.setText(getString(R.string.current_folder, uriString));
            }
        } else {
            tvDefaultFolder.setText(R.string.no_default_folder);
        }
    }

    private void updateCacheSize() {
        new Thread(() -> {
            long sizeBytes = cacheManager.getCacheSize();
            String sizeText;
            if (sizeBytes < 1024) {
                sizeText = sizeBytes + " B";
            } else if (sizeBytes < 1024 * 1024) {
                sizeText = String.format("%.1f KB", sizeBytes / 1024.0);
            } else {
                sizeText = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
            }
            final String displayText = getString(R.string.cache_size, sizeText);
            runOnUiThread(() -> tvCacheSize.setText(displayText));
        }).start();
    }
}
