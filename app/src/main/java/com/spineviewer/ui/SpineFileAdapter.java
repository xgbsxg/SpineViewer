package com.spineviewer.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spineviewer.R;
import com.spineviewer.spine.SpineFileInfo;

import java.util.ArrayList;
import java.util.List;

public class SpineFileAdapter extends RecyclerView.Adapter<SpineFileAdapter.ViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(SpineFileInfo info);
        void onVersionChangeClick(SpineFileInfo info, int position);
    }

    private final Context context;
    private List<SpineFileInfo> items;
    private List<SpineFileInfo> allItems;
    private final OnFileClickListener listener;

    public SpineFileAdapter(Context context, List<SpineFileInfo> items, OnFileClickListener listener) {
        this.context = context;
        this.items = new ArrayList<>(items);
        this.allItems = new ArrayList<>(items);
        this.listener = listener;
    }

    public void setItems(List<SpineFileInfo> newItems) {
        allItems = new ArrayList<>(newItems);
        items = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            items = new ArrayList<>(allItems);
        } else {
            String q = query.toLowerCase().trim();
            items = new ArrayList<>();
            for (SpineFileInfo info : allItems) {
                if (info.name.toLowerCase().contains(q) ||
                        info.getVersionLabel().toLowerCase().contains(q)) {
                    items.add(info);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_spine_file, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SpineFileInfo info = items.get(position);

        holder.nameText.setText(info.name);
        holder.versionText.setText(info.getVersionLabel());

        // Format indicator
        holder.formatBadge.setText(info.isBinary ? "SKEL" : "JSON");

        // Atlas indicator
        if (info.hasAtlas()) {
            holder.atlasStatus.setVisibility(View.VISIBLE);
            holder.atlasStatus.setText("✓ Atlas");
        } else {
            holder.atlasStatus.setVisibility(View.VISIBLE);
            holder.atlasStatus.setText("⚠ No atlas");
        }

        // Version detection confidence
        if (info.detectedVersion == null) {
            holder.detectionBadge.setVisibility(View.VISIBLE);
            holder.detectionBadge.setText("? Auto-detect failed");
        } else if (info.selectedVersion != null && info.selectedVersion != info.detectedVersion) {
            holder.detectionBadge.setVisibility(View.VISIBLE);
            holder.detectionBadge.setText("⚙ Manual: " + info.selectedVersion.getDisplayName());
        } else {
            holder.detectionBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onFileClick(info));
        holder.changeVersionBtn.setOnClickListener(v ->
                listener.onVersionChangeClick(info, holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, versionText, formatBadge, atlasStatus, detectionBadge;
        ImageButton changeVersionBtn;

        ViewHolder(View v) {
            super(v);
            nameText = v.findViewById(R.id.tv_name);
            versionText = v.findViewById(R.id.tv_version);
            formatBadge = v.findViewById(R.id.tv_format);
            atlasStatus = v.findViewById(R.id.tv_atlas);
            detectionBadge = v.findViewById(R.id.tv_detection);
            changeVersionBtn = v.findViewById(R.id.btn_change_version);
        }
    }
}
