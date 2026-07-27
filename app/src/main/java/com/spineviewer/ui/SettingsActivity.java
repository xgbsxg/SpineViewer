package com.spineviewer.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.spineviewer.R;
import com.spineviewer.utils.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_FOLDER = 1001;

    private PreferenceManager prefManager;
    private TextView tvDefaultFolder;
    private Button btnSelectFolder;
    private Button btnClearFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefManager = new PreferenceManager(this);

        tvDefaultFolder = findViewById(R.id.tv_default_folder);
        btnSelectFolder = findViewById(R.id.btn_select_folder);
        btnClearFolder = findViewById(R.id.btn_clear_folder);

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

    private String getFullPathFromUri(Uri uri) {
        try {
            // 尝试通过 DocumentFile 获取完整路径
            DocumentFile doc = DocumentFile.fromTreeUri(this, uri);
            if (doc != null) {
                // 构建完整路径：递归获取父目录名称
                StringBuilder path = new StringBuilder();
                DocumentFile current = doc;
                java.util.ArrayList<String> segments = new java.util.ArrayList<>();
                while (current != null) {
                    String name = current.getName();
                    if (name != null && !name.isEmpty()) {
                        segments.add(0, name);
                    }
                    // 获取父目录（DocumentFile 没有直接的 getParent，通过遍历实现）
                    if (current.getUri().getPath() != null) {
                        // 尝试通过 URI 路径解析
                        String uriPath = current.getUri().getPath();
                        if (uriPath != null && uriPath.contains("/tree/")) {
                            // 这是根目录，停止
                            break;
                        }
                    }
                    // 尝试获取父 DocumentFile
                    try {
                        String uriStr = current.getUri().toString();
                        int lastSlash = uriStr.lastIndexOf('/');
                        if (lastSlash > 0) {
                            String parentStr = uriStr.substring(0, lastSlash);
                            if (parentStr.contains("/tree/")) {
                                current = DocumentFile.fromTreeUri(this, Uri.parse(parentStr));
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
                if (!segments.isEmpty()) {
                    return "/" + String.join("/", segments);
                }
                return doc.getName();
            }
            return uri.getPath();
        } catch (Exception e) {
            return uri.getPath();
        }
    }

    private void updateDisplay() {
        String uriString = prefManager.getDefaultFolderUri();
        if (uriString != null) {
            try {
                Uri uri = Uri.parse(uriString);
                String path = getFullPathFromUri(uri);
                if (path != null && !path.isEmpty()) {
                    tvDefaultFolder.setText(getString(R.string.current_folder, path));
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
