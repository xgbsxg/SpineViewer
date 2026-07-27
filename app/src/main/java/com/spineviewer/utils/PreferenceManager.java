package com.spineviewer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.spineviewer.spine.SpineFileInfo;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PreferenceManager {
    private static final String PREF_NAME = "spine_viewer_prefs";
    private static final String KEY_DEFAULT_FOLDER = "default_folder_uri";
    private static final String KEY_FILE_LIST = "file_list_json";
    private static final String KEY_LAST_SCAN_URI = "last_scan_uri";

    private final SharedPreferences prefs;
    private final Gson gson;

    public PreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveDefaultFolderUri(@Nullable String uriString) {
        prefs.edit().putString(KEY_DEFAULT_FOLDER, uriString).apply();
    }

    @Nullable
    public String getDefaultFolderUri() {
        return prefs.getString(KEY_DEFAULT_FOLDER, null);
    }

    public void saveFileList(List<SpineFileInfo> fileList) {
        String json = gson.toJson(fileList);
        prefs.edit().putString(KEY_FILE_LIST, json).apply();
    }

    public List<SpineFileInfo> getFileList() {
        String json = prefs.getString(KEY_FILE_LIST, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<SpineFileInfo>>(){}.getType();
        List<SpineFileInfo> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void clearFileList() {
        prefs.edit().remove(KEY_FILE_LIST).apply();
    }

    public void saveLastScanUri(String uriString) {
        prefs.edit().putString(KEY_LAST_SCAN_URI, uriString).apply();
    }

    @Nullable
    public String getLastScanUri() {
        return prefs.getString(KEY_LAST_SCAN_URI, null);
    }
}