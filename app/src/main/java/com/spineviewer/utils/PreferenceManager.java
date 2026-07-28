package com.spineviewer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.spineviewer.spine.SpineFileInfo;
import com.spineviewer.spine.SpineVersion;

import java.util.ArrayList;
import java.util.List;

public class PreferenceManager {
    private static final String PREF_NAME = "spine_viewer_prefs";
    private static final String KEY_DEFAULT_FOLDER = "default_folder_uri";
    private static final String KEY_FILE_LIST = "file_list_json";
    private static final String KEY_LAST_SCAN_URI = "last_scan_uri";
    private static final String KEY_DEFAULT_PREMULTIPLY_ALPHA = "default_premultiply_alpha";
    private static final String KEY_CACHE_INDEX = "cache_index_json";

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
        JsonArray array = new JsonArray();
        for (SpineFileInfo info : fileList) {
            JsonObject obj = new JsonObject();
            obj.addProperty("skeletonUri", info.skeletonUri.toString());
            if (info.atlasUri != null) {
                obj.addProperty("atlasUri", info.atlasUri.toString());
            }
            obj.addProperty("name", info.name);
            obj.addProperty("isBinary", info.isBinary);
            if (info.detectedVersion != null) {
                obj.addProperty("detectedVersion", info.detectedVersion.name());
            }
            if (info.selectedVersion != null) {
                obj.addProperty("selectedVersion", info.selectedVersion.name());
            }
            if (info.rawVersionString != null) {
                obj.addProperty("rawVersionString", info.rawVersionString);
            }
            obj.addProperty("fileSizeBytes", info.fileSizeBytes);

            JsonArray siblings = new JsonArray();
            for (Uri uri : info.siblingUris) {
                siblings.add(uri.toString());
            }
            obj.add("siblingUris", siblings);

            array.add(obj);
        }
        String json = gson.toJson(array);
        prefs.edit().putString(KEY_FILE_LIST, json).apply();
    }

    public List<SpineFileInfo> getFileList() {
        String json = prefs.getString(KEY_FILE_LIST, null);
        if (json == null) return new ArrayList<>();

        List<SpineFileInfo> list = new ArrayList<>();
        try {
            JsonArray array = gson.fromJson(json, JsonArray.class);
            for (int i = 0; i < array.size(); i++) {
                JsonObject obj = array.get(i).getAsJsonObject();
                Uri skeletonUri = Uri.parse(obj.get("skeletonUri").getAsString());
                Uri atlasUri = obj.has("atlasUri") ? Uri.parse(obj.get("atlasUri").getAsString()) : null;
                String name = obj.get("name").getAsString();
                boolean isBinary = obj.get("isBinary").getAsBoolean();

                SpineFileInfo info = new SpineFileInfo(skeletonUri, atlasUri, name, isBinary);

                if (obj.has("detectedVersion")) {
                    String v = obj.get("detectedVersion").getAsString();
                    info.detectedVersion = SpineVersion.valueOf(v);
                }
                if (obj.has("selectedVersion")) {
                    String v = obj.get("selectedVersion").getAsString();
                    info.selectedVersion = SpineVersion.valueOf(v);
                }
                if (obj.has("rawVersionString")) {
                    info.rawVersionString = obj.get("rawVersionString").getAsString();
                }
                info.fileSizeBytes = obj.get("fileSizeBytes").getAsLong();

                if (obj.has("siblingUris")) {
                    JsonArray siblings = obj.get("siblingUris").getAsJsonArray();
                    for (int j = 0; j < siblings.size(); j++) {
                        info.siblingUris.add(Uri.parse(siblings.get(j).getAsString()));
                    }
                }

                list.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
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

    public void setDefaultPremultiplyAlpha(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEFAULT_PREMULTIPLY_ALPHA, enabled).apply();
    }

    public boolean getDefaultPremultiplyAlpha() {
        return prefs.getBoolean(KEY_DEFAULT_PREMULTIPLY_ALPHA, false);
    }

    public void saveCacheIndex(String json) {
        prefs.edit().putString(KEY_CACHE_INDEX, json).apply();
    }

    @Nullable
    public String getCacheIndex() {
        return prefs.getString(KEY_CACHE_INDEX, null);
    }

    public void clearCacheIndex() {
        prefs.edit().remove(KEY_CACHE_INDEX).apply();
    }
}
