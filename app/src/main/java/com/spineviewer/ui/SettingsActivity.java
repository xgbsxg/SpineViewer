package com.spineviewer.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.spineviewer.R;
import com.spineviewer.utils.PreferenceManager;

import java.net.URLDecoder;

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

    /**
     * 尝试将 SAF URI 转换为可读的文件系统路径（仅适用于外部存储）
     */
    private String getReadablePath(Uri uri) {
        try {
            String path = uri.getPath();
            if (path == null) return null;

            // 例如: /tree/primary:Download/Spine
            if (path.startsWith("/tree/")) {
                String encoded = path.substring(6); // 去掉 "/tree/"
                // 解码 URL 编码
                String decoded = URLDecoder.decode(encoded, "UTF-8");
                // 检查是否以 "primary:" 开头
                if (decoded.startsWith("primary:")) {
                    String relative = decoded.substring(8); // 去掉 "primary:"
                    // 获取外部存储根目录
                    String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
                    // 拼接完整路径
                    if (relative.isEmpty()) {
                        return extStorage;
                    } else {
                        return extStorage + "/" + relative;
                    }
                } else {
                    // 其他存储（如 SD 卡），可能以 "XXXX-XXXX:" 开头，暂无法解析
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
