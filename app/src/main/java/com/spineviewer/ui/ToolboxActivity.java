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
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }

                String atlasText = content.toString();
                String[] lines = atlasText.split("\n");

                List<String> newLines = new ArrayList<>();
                boolean inRegion = false;

                for (String line : lines) {
                    String trimmed = line.trim();

                    // 检查是否是区域名称行（非空行、非缩进、且不是页面属性行）
                    if (!trimmed.isEmpty() && !trimmed.startsWith(" ") && !trimmed.startsWith("\t")) {
                        // 检查是否是页面名称行（以 .png 结尾）
                        if (trimmed.endsWith(".png") || trimmed.endsWith(".jpg") || trimmed.endsWith(".webp")) {
                            inRegion = false;
                            newLines.add(line);
                            continue;
                        }
                        // 否则是区域名称
                        inRegion = true;
                        newLines.add(line);
                        continue;
                    }

                    // 处理缩进的属性行
                    if (inRegion && (trimmed.startsWith(" ") || trimmed.startsWith("\t"))) {
                        String property = trimmed.trim();

                        // 处理 bounds: x, y, width, height
                        if (property.startsWith("bounds:")) {
                            String[] parts = property.replace("bounds:", "").trim().split("\\s*,\\s*");
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

                                    newLines.add("  bounds: " + newX + ", " + newY + ", " + newWidth + ", " + newHeight);
                                    continue;
                                } catch (NumberFormatException e) {
                                    // 解析失败，保留原行
                                }
                            }
                        }

                        // 处理 xy: x, y (旧格式)
                        if (property.startsWith("xy:")) {
                            String[] parts = property.replace("xy:", "").trim().split("\\s*,\\s*");
                            if (parts.length >= 2) {
                                try {
                                    int x = Integer.parseInt(parts[0].trim());
                                    int y = Integer.parseInt(parts[1].trim());
                                    int newX = Math.round(x * scale);
                                    int newY = Math.round(y * scale);
                                    newLines.add("  xy: " + newX + ", " + newY);
                                    continue;
                                } catch (NumberFormatException e) {
                                    // 解析失败，保留原行
                                }
                            }
                        }

                        // 处理 size: width, height (旧格式)
                        if (property.startsWith("size:")) {
                            String[] parts = property.replace("size:", "").trim().split("\\s*,\\s*");
                            if (parts.length >= 2) {
                                try {
                                    int width = Integer.parseInt(parts[0].trim());
                                    int height = Integer.parseInt(parts[1].trim());
                                    int newWidth = Math.round(width * scale);
                                    int newHeight = Math.round(height * scale);
                                    newLines.add("  size: " + newWidth + ", " + newHeight);
                                    continue;
                                } catch (NumberFormatException e) {
                                    // 解析失败，保留原行
                                }
                            }
                        }

                        // 处理 rotate: true/false
                        if (property.startsWith("rotate:")) {
                            String value = property.replace("rotate:", "").trim();
                            if (value.equals("true") || value.equals("false")) {
                                newLines.add("  rotate: " + value);
                                continue;
                            }
                        }

                        // 处理 index: 数字
                        if (property.startsWith("index:")) {
                            String value = property.replace("index:", "").trim();
                            newLines.add("  index: " + value);
                            continue;
                        }

                        // 处理 offsets: x, y, width, height
                        if (property.startsWith("offsets:")) {
                            String[] parts = property.replace("offsets:", "").trim().split("\\s*,\\s*");
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
                                    newLines.add("  offsets: " + newX + ", " + newY + ", " + newWidth + ", " + newHeight);
                                    continue;
                                } catch (NumberFormatException e) {
                                    // 解析失败，保留原行
                                }
                            }
                        }

                        // 处理 split: x, y, width, height
                        if (property.startsWith("split:")) {
                            String[] parts = property.replace("split:", "").trim().split("\\s*,\\s*");
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
                                    newLines.add("  split: " + newX + ", " + newY + ", " + newWidth + ", " + newHeight);
                                    continue;
                                } catch (NumberFormatException e) {
                                    // 解析失败，保留原行
                                }
                            }
                        }

                        // 处理 pad: x, y, width, height
                        if (property.startsWith("pad:")) {
                            String[] parts = property.replace("pad:", "").trim().split("\\s*,\\s*");
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
                                    newLines.add("  pad: " + newX + ", " + newY + ", " + newWidth + ", " + newHeight);
                                    continue;
                                } catch (NumberFormatException e) {
                                    // 解析失败，保留原行
                                }
                            }
                        }

                        // 其他属性（如 pma、filter、repeat 等）不修改，直接保留
                        newLines.add(line);
                        continue;
                    }

                    // 非区域行（页面属性或空行），直接保留
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
