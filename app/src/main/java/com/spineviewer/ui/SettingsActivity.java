package com.spineviewer.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.spineviewer.R;
import com.spineviewer.utils.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_FOLDER = 1001;

    private PreferenceManager prefManager;
    private TextView tvDefaultFolder;
    private Button btnSelectFolder;
    private Button btnClearFolder;
    private Switch switchDefaultPremultiply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefManager = new PreferenceManager(this);

        tvDefaultFolder = findViewById(R.id.tv_default_folder);
        btnSelectFolder = findViewById(R.id.btn_select_folder);
        btnClearFolder = findViewById(R.id.btn_clear_folder);
        switchDefaultPremultiply = findViewById(R.id.switch_default_premultiply);

        switchDefaultPremultiply.setChecked(prefManager.getDefaultPremultiplyAlpha());

        switchDefaultPremultiply.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefManager.setDefaultPremultiplyAlpha(isChecked);
        });

        btnSelectFolder.setOnClickListener(v -> openFolderPicker());
        btnClearFolder.setOnClickListener(v -> {
            prefManager.saveDefaultFolderUri(null);
            updateDisplay();
            Toast.makeText(this, R.string.default_folder_cleared, Toast.LENGTH_SHORT).show();
        });

        updateDisplay();
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
                    String extStorage = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
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
}
