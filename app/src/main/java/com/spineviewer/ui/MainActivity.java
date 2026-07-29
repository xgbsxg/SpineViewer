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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
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
    private TextView tvScanProgress;
    private List<SpineFileInfo> fileList = new ArrayList<>();
    private PreferenceManager prefManager;

    private ActivityResultLauncher<Uri> folderPickerLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String> filePicker;

    private MenuItem refreshMenuItem;
    private Animation refreshAnimation;
    private boolean isScanning = false;

    private ActionMode actionMode;
    private ActionModeCallback actionModeCallback;

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
        tvScanProgress = findViewById(R.id.tv_scan_progress);

        adapter = new SpineFileAdapter(this, fileList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        setupSwipeToDelete();
        setupMultiSelect();

        refreshAnimation = AnimationUtils.loadAnimation(this, R.drawable.anim_refresh_rotate);

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
            if (fileList.isEmpty()) {
                showWelcomeDialog();
            }
        }

        updateEmptyView();
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (adapter.isMultiSelectMode()) {
                    adapter.notifyItemChanged(viewHolder.getAdapterPosition());
                    return;
                }
                int position = viewHolder.getAdapterPosition();
                if (position < 0 || position >= fileList.size()) {
                    adapter.notifyItemChanged(position);
                    return;
                }
                SpineFileInfo removed = fileList.remove(position);
                adapter.setItems(fileList);
                prefManager.saveFileList(fileList);
                updateEmptyView();
                Toast.makeText(MainActivity.this,
                        getString(R.string.delete_message, removed.name), Toast.LENGTH_SHORT).show();
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void setupMultiSelect() {
        actionModeCallback = new ActionModeCallback();

        adapter.setOnMultiSelectListener(count -> {
            if (actionMode != null) {
                if (count == 0) {
                    actionMode.finish();
                } else {
                    actionMode.setTitle(getString(R.string.selected_count, count));
                }
            }
        });
    }

    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_main, menu);
            MenuItem searchItem = menu.findItem(R.id.action_search);
            if (searchItem != null) searchItem.setVisible(false);
            MenuItem refreshItem = menu.findItem(R.id.action_refresh);
            if (refreshItem != null) refreshItem.setVisible(false);
            MenuItem clearItem = menu.findItem(R.id.action_clear);
            if (clearItem != null) clearItem.setVisible(false);
            MenuItem settingsItem = menu.findItem(R.id.action_settings);
            if (settingsItem != null) settingsItem.setVisible(false);
            MenuItem openFileItem = menu.findItem(R.id.action_open_file);
            if (openFileItem != null) openFileItem.setVisible(false);

            menu.add(0, R.id.action_select_all, 0, R.string.select_all)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, R.id.action_delete_selected, 0, R.string.delete)
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

            adapter.setMultiSelectMode(true);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_select_all) {
                MenuItem selectAllItem = mode.getMenu().findItem(R.id.action_select_all);
                if (adapter.getSelectedCount() == adapter.getItemCount()) {
                    adapter.deselectAll();
                    if (selectAllItem != null) {
                        selectAllItem.setTitle(R.string.select_all);
                    }
                } else {
                    adapter.selectAll();
                    if (selectAllItem != null) {
                        selectAllItem.setTitle(R.string.deselect_all);
                    }
                }
                return true;
            }
            if (id == R.id.action_delete_selected) {
                deleteSelected();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            adapter.setMultiSelectMode(false);
        }
    }

    private void deleteSelected() {
        List<SpineFileInfo> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) return;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.batch_delete_title)
                .setMessage(getString(R.string.batch_delete_message, selected.size()))
                .setPositiveButton(R.string.delete, (d, which) -> {
                    fileList.removeAll(selected);
                    adapter.removeItems(selected);
                    prefManager.saveFileList(fileList);
                    updateEmptyView();
                    Toast.makeText(this, getString(R.string.deleted, selected.size()), Toast.LENGTH_SHORT).show();
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(ContextCompat.getColor(this, R.color.accent));
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void showWelcomeDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_message)
                .setPositiveButton(R.string.go_to_settings, (d, which) -> {
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.later, null)
                .setCancelable(true)
                .create();
        dialog.show();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(ContextCompat.getColor(this, R.color.accent));
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
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

        refreshMenuItem = menu.findItem(R.id.action_refresh);

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
            if (!isScanning) {
                refreshList();
            }
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

    private void startRefreshAnimation() {
        if (refreshMenuItem == null) return;
        View actionView = refreshMenuItem.getActionView();
        if (actionView instanceof ImageView) {
            ImageView refreshIcon = (ImageView) actionView;
            refreshIcon.setImageDrawable(refreshMenuItem.getIcon());
            refreshIcon.startAnimation(refreshAnimation);
        }
    }

    private void stopRefreshAnimation() {
        if (refreshMenuItem == null) return;
        View actionView = refreshMenuItem.getActionView();
        if (actionView instanceof ImageView) {
            ImageView refreshIcon = (ImageView) actionView;
            refreshIcon.clearAnimation();
            refreshIcon.setImageDrawable(null);
        }
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
        if (isScanning) return;
        isScanning = true;
        startRefreshAnimation();

        loadingView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        if (tvScanProgress != null) {
            tvScanProgress.setText(R.string.scanning);
        }

        boolean scanSubdirs = prefManager.getScanSubdirectories();

        new Thread(() -> {
            List<SpineFileInfo> found = FileScanner.scanForSpineFiles(this, treeUri, scanSubdirs,
                    new FileScanner.ScanCallback() {
                        @Override
                        public void onFileFound(SpineFileInfo info, int totalSoFar) {
                            runOnUiThread(() -> {
                                if (tvScanProgress != null) {
                                    tvScanProgress.setText(
                                            getString(R.string.scan_progress, totalSoFar));
                                }
                            });
                        }

                        @Override
                        public void onScanSubfolder(String folderName) {
                            runOnUiThread(() -> {
                                if (tvScanProgress != null) {
                                    tvScanProgress.setText(
                                            getString(R.string.scanning_subfolders, folderName));
                                }
                            });
                        }
                    });

            runOnUiThread(() -> {
                isScanning = false;
                stopRefreshAnimation();
                loadingView.setVisibility(View.GONE);
                if (!found.isEmpty()) {
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
                    if (fileList.isEmpty()) {
                        updateEmptyView();
                    }
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
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback);
        }
        adapter.toggleSelection(position);
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_version_title, info.name))
                .setSingleChoiceItems(labels, currentIdx, (d, which) -> {
                    info.selectedVersion = versions[which];
                    prefManager.saveFileList(fileList);
                    adapter.notifyItemChanged(position);
                    d.dismiss();
                })
                .setNegativeButton(R.string.close, null)
                .create();
        dialog.show();
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
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
