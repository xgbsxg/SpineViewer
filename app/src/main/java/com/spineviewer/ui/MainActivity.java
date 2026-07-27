package com.spineviewer.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spineviewer.R;
import com.spineviewer.spine.SpineFileDetector;
import com.spineviewer.spine.SpineFileInfo;
import com.spineviewer.spine.SpineVersion;
import com.spineviewer.utils.FileScanner;
import com.spineviewer.utils.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements SpineFileAdapter.OnFileClickListener {

    private RecyclerView recyclerView;
    private SpineFileAdapter adapter;
    private View emptyView;
    private View loadingView;
    private List<SpineFileInfo> fileList = new ArrayList<>();
    private PreferenceManager prefManager;

    private ActivityResultLauncher<Uri> folderPickerLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String> filePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefManager = new PreferenceManager(this);

        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty_view);
        loadingView = findViewById(R.id.loading_view);

        adapter = new SpineFileAdapter(this, fileList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        prefManager.saveDefaultFolderUri(uri.toString());
                        prefManager.saveLastScanUri(uri.toString());
                        scanFolder(uri);
                    }
                });

        filePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        openSingleFile(uri);
                    }
                });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = true;
                    for (Boolean v : result.values()) {
                        if (!v) { granted = false; break; }
                    }
                    if (granted) openFolderPicker();
                    else Toast.makeText(this, R.string.need_permission, Toast.LENGTH_LONG).show();
                });

        handleIncomingIntent(getIntent());

        loadPersistedList();

        String defaultUri = prefManager.getDefaultFolderUri();
        if (defaultUri != null) {
            Uri uri = Uri.parse(defaultUri);
            if (uri != null) {
                scanFolder(uri);
            }
        } else {
            // 首次启动，显示引导对话框
            if (fileList.isEmpty()) {
                showWelcomeDialog();
            }
        }

        updateEmptyView();
    }

    private void showWelcomeDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_message)
                .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.later, null)
                .setCancelable(true)
                .show();
    }

    private void loadPersistedList() {
        List<SpineFileInfo> saved = prefManager.getFileList();
        if (!saved.isEmpty()) {
            fileList.clear();
            fileList.addAll(saved);
            adapter.setItems(fileList);
            updateEmptyView();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }
            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            refreshList();
            return true;
        }
        if (id == R.id.action_open_file) {
            filePicker.launch("*/*");
            return true;
        }
        if (id == R.id.action_clear) {
            clearList();
            return true;
        }
        if (id == R.id.action_settings) {
            openSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshList() {
        String defaultUri = prefManager.getDefaultFolderUri();
        if (defaultUri != null) {
            Uri uri = Uri.parse(defaultUri);
            if (uri != null) {
                scanFolder(uri);
            } else {
                Toast.makeText(this, "默认文件夹无效，请重新设置", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "请先在设置中选择默认文件夹", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void clearList() {
        fileList.clear();
        adapter.setItems(fileList);
        prefManager.clearFileList();
        updateEmptyView();
        Toast.makeText(this, R.string.list_cleared, Toast.LENGTH_SHORT).show();
    }

    private void openFolderPicker() {
        if (needsPermissions()) {
            requestPermissions();
            return;
        }
        folderPickerLauncher.launch(null);
    }

    private boolean needsPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return false;
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private void scanFolder(Uri treeUri) {
        loadingView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        new Thread(() -> {
            List<SpineFileInfo> found = FileScanner.scanForSpineFiles(this, treeUri);
            runOnUiThread(() -> {
                loadingView.setVisibility(View.GONE);
                if (!found.isEmpty()) {
                    // 合并结果：保留持久化列表，但替换为扫描结果
                    fileList.clear();
                    fileList.addAll(found);
                    prefManager.saveFileList(fileList);
                    adapter.setItems(fileList);
                    updateEmptyView();
                    Toast.makeText(this,
                            getString(R.string.found_files, found.size()),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.no_files_found, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void openSingleFile(Uri uri) {
        new Thread(() -> {
            SpineFileDetector.DetectionResult result = SpineFileDetector.detect(this, uri);
            String name = getFileNameFromUri(uri);
            SpineFileInfo info = new SpineFileInfo(uri, null, name, result.isBinaryFormat);
            info.detectedVersion = result.detectedVersion;
            info.selectedVersion = result.detectedVersion;
            info.rawVersionString = result.rawVersionString;
            runOnUiThread(() -> {
                openPreview(info);
            });
        }).start();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null) {
            openSingleFile(data);
        }
    }

    @Override
    public void onFileClick(SpineFileInfo info) {
        openPreview(info);
    }

    @Override
    public void onVersionChangeClick(SpineFileInfo info, int position) {
        showVersionPicker(info, position);
    }

    @Override
    public void onFileLongClick(SpineFileInfo info, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除条目")
                .setMessage("确定要删除 \"" + info.name + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    fileList.remove(position);
                    adapter.setItems(fileList);
                    prefManager.saveFileList(fileList);
                    updateEmptyView();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openPreview(SpineFileInfo info) {
        Intent intent = new Intent(this, SpinePreviewActivity.class);
        intent.putExtra(SpinePreviewActivity.EXTRA_SKELETON_URI, info.skeletonUri.toString());
        if (info.atlasUri != null)
            intent.putExtra(SpinePreviewActivity.EXTRA_ATLAS_URI, info.atlasUri.toString());
        intent.putExtra(SpinePreviewActivity.EXTRA_VERSION, info.getEffectiveVersion().name());
        intent.putExtra(SpinePreviewActivity.EXTRA_NAME, info.name);
        if (!info.siblingUris.isEmpty()) {
            intent.putParcelableArrayListExtra(SpinePreviewActivity.EXTRA_TEXTURE_URIS,
                new ArrayList<>(info.siblingUris));
        }
        startActivity(intent);
    }

    private void showVersionPicker(SpineFileInfo info, int position) {
        SpineVersion[] versions = SpineVersion.values();
        String[] labels = new String[versions.length];
        int currentIdx = 0;
        for (int i = 0; i < versions.length; i++) {
            labels[i] = getString(R.string.version_item, versions[i].getDisplayName());
            if (versions[i] == info.getEffectiveVersion()) currentIdx = i;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_version_title, info.name))
                .setSingleChoiceItems(labels, currentIdx, (dialog, which) -> {
                    info.selectedVersion = versions[which];
                    prefManager.saveFileList(fileList);
                    adapter.notifyItemChanged(position);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void updateEmptyView() {
        if (fileList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String path = uri.getPath();
        if (path == null) return "skeleton";
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }
}
