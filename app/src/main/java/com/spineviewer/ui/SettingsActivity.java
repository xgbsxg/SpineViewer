package com.spineviewer.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.spineviewer.R;
import com.spineviewer.utils.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private PreferenceManager prefManager;
    private TextView tvDefaultFolder;
    private Button btnSelectFolder;
    private Button btnClearFolder;

    private ActivityResultLauncher<Void> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefManager = new PreferenceManager(this);

        tvDefaultFolder = findViewById(R.id.tv_default_folder);
        btnSelectFolder = findViewById(R.id.btn_select_folder);
        btnClearFolder = findViewById(R.id.btn_clear_folder);

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            String uriString = uri.toString();
                            prefManager.saveDefaultFolderUri(uriString);
                            prefManager.saveLastScanUri(uriString);
                            updateDisplay();
                            Toast.makeText(SettingsActivity.this, "Default folder updated", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        btnSelectFolder.setOnClickListener(v -> folderPickerLauncher.launch(null));

        btnClearFolder.setOnClickListener(v -> {
            prefManager.saveDefaultFolderUri(null);
            updateDisplay();
            Toast.makeText(this, "Default folder cleared", Toast.LENGTH_SHORT).show();
        });

        updateDisplay();
    }

    private void updateDisplay() {
        String uri = prefManager.getDefaultFolderUri();
        if (uri != null) {
            tvDefaultFolder.setText("Current: " + uri);
        } else {
            tvDefaultFolder.setText("No default folder set");
        }
    }
}
