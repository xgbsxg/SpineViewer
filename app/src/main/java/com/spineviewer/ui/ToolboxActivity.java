package com.spineviewer.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.spineviewer.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class ToolboxActivity extends AppCompatActivity {

    private TextView tvAtlasStatus;
    private EditText etScaleFactor;
    private Button btnSelectAtlas, btnScaleAndSave;
    private ProgressBar progressBar;

    private Uri selectedAtlasUri;
    private String selectedAtlasName;

    private final ActivityResultLauncher<String> atlasPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedAtlasUri = uri;
                    selectedAtlasName = getFileName(uri);
                    tvAtlasStatus.setText(getString(R.string.atlas_selected, selectedAtlasName));
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toolbox);

        tvAtlasStatus = findViewById(R.id.tv_atlas_status);
        etScaleFactor = findViewById(R.id.et_scale_factor);
        btnSelectAtlas = findViewById(R.id.btn_select_atlas);
        btnScaleAndSave = findViewById(R.id.btn_scale_and_save);
        progressBar = findViewById(R.id.progress_bar);

        btnSelectAtlas.setOnClickListener(v -> atlasPickerLauncher.launch("*/*"));
        btnScaleAndSave.setOnClickListener(v -> startScaleTask());
    }

    private String getFileName(Uri uri) {
        String path = uri.getPath();
        if (path == null) return "unknown.atlas";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void startScaleTask() {
        if (selectedAtlasUri == null) {
            Toast.makeText(this, R.string.no_atlas_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        String scaleStr = etScaleFactor.getText().toString().trim();
        if (scaleStr.isEmpty()) {
            Toast.makeText(this, R.string.enter_scale_factor, Toast.LENGTH_SHORT).show();
            return;
        }

        float scale;
        try {
            scale = Float.parseFloat(scaleStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.invalid_scale_factor, Toast.LENGTH_SHORT).show();
            return;
        }

        if (scale < 0.1f || scale > 2.0f) {
            Toast.makeText(this, R.string.invalid_scale_factor, Toast.LENGTH_SHORT).show();
            return;
        }

        new ScaleAtlasTask().execute(selectedAtlasUri, scale);
    }

    private class ScaleAtlasTask extends AsyncTask<Object, Void, String> {

        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(ToolboxActivity.this);
            progressDialog.setMessage(getString(R.string.processing));
            progressDialog.setCancelable(false);
            progressDialog.show();
            progressBar.setVisibility(ProgressBar.VISIBLE);
        }

        @Override
        protected String doInBackground(Object... params) {
            Uri uri = (Uri) params[0];
            float scale = (float) params[1];

            try {
                // 读取全部行
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }

                List<String> newLines = new ArrayList<>();
                boolean inPage = false;
                boolean inRegion = false;

                for (String line : lines) {
                    String trimmed = line.trim();
                    String leadingWhitespace = getLeadingWhitespace(line);
                    boolean isIndented = leadingWhitespace.length() > 0;

                    // 检测图片名称行（新页面开始）
                    if (trimmed.endsWith(".png") || trimmed.endsWith(".jpg") || trimmed.endsWith(".webp")) {
                        inPage = true;
                        inRegion = false;
                        newLines.add(line);
                        continue;
                    }

                    // 检测区域名称行（非缩进，非图片名，非空）
                    if (!isIndented && !trimmed.isEmpty() && !trimmed.endsWith(".png") && !trimmed.endsWith(".jpg") && !trimmed.endsWith(".webp")) {
                        inPage = false;
                        inRegion = true;
                        newLines.add(line);
                        continue;
                    }

                    // 处理缩进行（属性）
                    if (isIndented) {
                        // 页面属性（只在 inPage 时处理）
                        if (inPage && !inRegion) {
                            String modifiedLine = handlePageProperty(line, trimmed, scale);
                            newLines.add(modifiedLine);
                            continue;
                        }

                        // 区域属性（只在 inRegion 时处理）
                        if (inRegion) {
                            String modifiedLine = handleRegionProperty(line, trimmed, scale);
                            newLines.add(modifiedLine);
                            continue;
                        }
                    }

                    // 其他行（空行或无法识别的行）直接保留
                    newLines.add(line);
                }

                // 构建新内容
                StringBuilder newContent = new StringBuilder();
                for (String l : newLines) {
                    newContent.append(l).append("\n");
                }

                // 保存到 Download 文件夹
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }

                String baseName = selectedAtlasName;
                if (baseName.contains(".")) {
                    baseName = baseName.substring(0, baseName.lastIndexOf('.'));
                }
                String newFileName = baseName + "_scaled.atlas";
                File outputFile = new File(downloadDir, newFileName);

                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile))) {
                    writer.write(newContent.toString());
                }

                return outputFile.getAbsolutePath();

            } catch (Exception e) {
                return "ERROR:" + e.getMessage();
            }
        }

        private String getLeadingWhitespace(String line) {
            int count = 0;
            while (count < line.length() && (line.charAt(count) == ' ' || line.charAt(count) == '\t')) {
                count++;
            }
            return line.substring(0, count);
        }

        private String handlePageProperty(String originalLine, String trimmed, float scale) {
            // 只修改 size 行
            if (trimmed.startsWith("size:")) {
                String[] parts = trimmed.replace("size:", "").trim().split("\\s*,\\s*");
                if (parts.length >= 2) {
                    try {
                        int width = Integer.parseInt(parts[0].trim());
                        int height = Integer.parseInt(parts[1].trim());
                        int newWidth = Math.round(width * scale);
                        int newHeight = Math.round(height * scale);
                        String newLine = "size: " + newWidth + ", " + newHeight;
                        // 保留原有缩进
                        String leading = getLeadingWhitespace(originalLine);
                        return leading + newLine;
                    } catch (NumberFormatException e) {
                        // 解析失败，保留原行
                    }
                }
            }
            // 其他页面属性（如 format, filter, repeat）不修改
            return originalLine;
        }

        private String handleRegionProperty(String originalLine, String trimmed, float scale) {
            String leading = getLeadingWhitespace(originalLine);

            // 处理 bounds: x, y, width, height
            if (trimmed.startsWith("bounds:")) {
                String[] parts = trimmed.replace("bounds:", "").trim().split("\\s*,\\s*");
                if (parts.length >= 4) {
                    try {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int width = Integer.parseInt(parts[2].trim());
                        int height = Integer.parseInt(parts[3].trim());
                        int newX = Math.round(x * scale);
                        int newY = Math.round(y * scale);
                        int newWidth = Math.round(width * scale);
                        int newHeight = Math.round(height * scale);
                        return leading + "bounds: " + newX + ", " + newY + ", " + newWidth + ", " + newHeight;
                    } catch (NumberFormatException e) {
                        // 解析失败，保留原行
                    }
                }
            }

            // 处理 xy: x, y (旧格式)
            if (trimmed.startsWith("xy:")) {
                String[] parts = trimmed.replace("xy:", "").trim().split("\\s*,\\s*");
                if (parts.length >= 2) {
                    try {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int newX = Math.round(x * scale);
                        int newY = Math.round(y * scale);
                        return leading + "xy: " + newX + ", " + newY;
                    } catch (NumberFormatException e) {
                        // 解析失败，保留原行
                    }
                }
            }

            // 处理 size: width, height (旧格式，区域尺寸)
            if (trimmed.startsWith("size:")) {
                // 注意：区域属性也有 size，但格式与页面 size 相同，我们也要缩放
                String[] parts = trimmed.replace("size:", "").trim().split("\\s*,\\s*");
                if (parts.length >= 2) {
                    try {
                        int width = Integer.parseInt(parts[0].trim());
                        int height = Integer.parseInt(parts[1].trim());
                        int newWidth = Math.round(width * scale);
                        int newHeight = Math.round(height * scale);
                        return leading + "size: " + newWidth + ", " + newHeight;
                    } catch (NumberFormatException e) {
                        // 解析失败，保留原行
                    }
                }
            }

            // 处理 offsets: x, y, width, height
            if (trimmed.startsWith("offsets:")) {
                String[] parts = trimmed.replace("offsets:", "").trim().split("\\s*,\\s*");
                if (parts.length >= 4) {
                    try {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int width = Integer.parseInt(parts[2].trim());
                        int height = Integer.parseInt(parts[3].trim());
                        int newX = Math.round(x * scale);
                        int newY = Math.round(y * scale);
                        int newWidth = Math.round(width * scale);
                        int newHeight = Math.round(height * scale);
                        return leading + "offsets: " + newX + ", " + newY + ", " + newWidth + ", " + newHeight;
                    } catch (NumberFormatException e) {
                        // 解析失败，保留原行
                    }
                }
            }

            // 处理 split: x, y, width, height
            if (trimmed.startsWith("split:")) {
                String[] parts = trimmed.replace("split:", "").trim().split("\\s*,\\s*");
                if (parts.length >= 4) {
                    try {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int width = Integer.parseInt(parts[2].trim());
                        int height = Integer.parseInt(parts[3].trim());
                        int newX = Math.round(x * scale);
                        int newY = Math.round(y * scale);
                        int newWidth = Math.round(width * scale);
                        int newHeight = Math.round(height * scale);
                        return leading + "split: " + newX + ", " + newY + ", " + newWidth + ", " + newHeight;
                    } catch (NumberFormatException e) {
                        // 解析失败，保留原行
                    }
                }
            }

            // 处理 pad: x, y, width, height
            if (trimmed.startsWith("pad:")) {
                String[] parts = trimmed.replace("pad:", "").trim().split("\\s*,\\s*");
                if (parts.length >= 4) {
                    try {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int width = Integer.parseInt(parts[2].trim());
                        int height = Integer.parseInt(parts[3].trim());
                        int newX = Math.round(x * scale);
                        int newY = Math.round(y * scale);
                        int newWidth = Math.round(width * scale);
                        int newHeight = Math.round(height * scale);
                        return leading + "pad: " + newX + ", " + newY + ", " + newWidth + ", " + newHeight;
                    } catch (NumberFormatException e) {
                        // 解析失败，保留原行
                    }
                }
            }

            // 其他区域属性（如 rotate, index, pma, filter, repeat 等）不修改
            return originalLine;
        }

        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();
            progressBar.setVisibility(ProgressBar.GONE);

            if (result.startsWith("ERROR:")) {
                String error = result.substring(6);
                Toast.makeText(ToolboxActivity.this,
                        getString(R.string.scale_failed, error),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(ToolboxActivity.this,
                        getString(R.string.scale_success) + "\n" + result,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
