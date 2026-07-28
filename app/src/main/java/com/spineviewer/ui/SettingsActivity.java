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
import androidx.documentfile.provider.DocumentFile;

import com.spineviewer.R;
import com.spineviewer.utils.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_FOLDER = 1001;
    private static final int REQUEST_CODE_STORAGE = 1002;

    private PreferenceManager prefManager;
    private TextView tvDefaultFolder;
    private TextView tvStorageDirectory;
    private Button btnSelectFolder;
    private Button btnClearFolder;
    private Button btnSelectStorage;
    private Button btnClearStorage;
    private Switch switchDefaultPremultiply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefManager = new PreferenceManager(this);

        tvDefaultFolder = findViewById(R.id.tv_default_folder);
        tvStorageDirectory = findViewById(R.id.tv_storage_directory);
        btnSelectFolder = findViewById(R.id.btn_select_folder);
        btnClearFolder = findViewById(R.id.btn_clear_folder);
        btnSelectStorage = findViewById(R.id.btn_select_storage);
        btnClearStorage = findViewById(R.id.btn_clear_storage);
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

        btnSelectStorage.setOnClickListener(v -> openStoragePicker());
        btnClearStorage.setOnClickListener(v -> {
            prefManager.saveStorageDirectoryUri(null);
            updateDisplay();
            Toast.makeText(this, R.string.storage_directory_cleared, Toast.LENGTH_SHORT).show();
        });

        updateDisplay();
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_FOLDER);
    }

    private void openStoragePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_STORAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null || resultCode != RESULT_OK) return;

        Uri uri = data.getData();
        if (uri == null) return;

        getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (requestCode == REQUEST_CODE_FOLDER) {
            prefManager.saveDefaultFolderUri(uri.toString());
            prefManager.saveLastScanUri(uri.toString());
            updateDisplay();
            Toast.makeText(this, R.string.default_folder_updated, Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQUEST_CODE_STORAGE) {
            prefManager.saveStorageDirectoryUri(uri.toString());
            updateDisplay();
            Toast.makeText(this, R.string.storage_directory_updated, Toast.LENGTH_SHORT).show();
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
        String folderUri = prefManager.getDefaultFolderUri();
        if (folderUri != null) {
            try {
                Uri uri = Uri.parse(folderUri);
                String readable = getReadablePath(uri);
                if (readable != null && !readable.isEmpty()) {
                    tvDefaultFolder.setText(getString(R.string.current_folder, readable));
                } else {
                    tvDefaultFolder.setText(getString(R.string.current_folder, folderUri));
                }
            } catch (Exception e) {
                tvDefaultFolder.setText(getString(R.string.current_folder, folderUri));
            }
        } else {
            tvDefaultFolder.setText(R.string.no_default_folder);
        }

        String storageUri = prefManager.getStorageDirectoryUri();
        if (storageUri != null) {
            try {
                Uri uri = Uri.parse(storageUri);
                String readable = getReadablePath(uri);
                if (readable != null && !readable.isEmpty()) {
                    tvStorageDirectory.setText(getString(R.string.current_storage_directory, readable));
                } else {
                    tvStorageDirectory.setText(getString(R.string.current_storage_directory, storageUri));
                }
            } catch (Exception e) {
                tvStorageDirectory.setText(getString(R.string.current_storage_directory, storageUri));
            }
        } else {
            tvStorageDirectory.setText(R.string.no_storage_directory);
        }
    }
}
