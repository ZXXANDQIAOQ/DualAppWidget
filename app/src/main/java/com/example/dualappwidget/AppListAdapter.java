package com.example.dualappwidget;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 已安装应用列表适配器。右侧角标显示该应用被放在了"上"还是"下"。
 */
public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppInfo appInfo);
    }

    private final List<AppInfo> items = new ArrayList<>();
    private final OnAppClickListener listener;
    private String topPackage;
    private String bottomPackage;

    public AppListAdapter(OnAppClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<AppInfo> apps) {
        items.clear();
        if (apps != null) {
            items.addAll(apps);
        }
        notifyDataSetChanged();
    }

    public void setSelection(String topPackage, String bottomPackage) {
        this.topPackage = topPackage;
        this.bottomPackage = bottomPackage;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo info = items.get(position);
        holder.label.setText(info.getLabel());
        holder.packageName.setText(info.getPackageName());
        if (info.getIcon() != null) {
            holder.icon.setImageDrawable(info.getIcon());
        } else {
            holder.icon.setImageResource(R.drawable.ic_placeholder);
        }

        String badge = buildBadge(info.getPackageName());
        if (TextUtils.isEmpty(badge)) {
            holder.badge.setVisibility(View.GONE);
        } else {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(badge);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppClick(info);
            }
        });
    }

    private String buildBadge(String packageName) {
        boolean isTop = packageName != null && packageName.equals(topPackage);
        boolean isBottom = packageName != null && packageName.equals(bottomPackage);
        if (isTop && isBottom) {
            return "上/下";
        }
        if (isTop) {
            return "上";
        }
        if (isBottom) {
            return "下";
        }
        return "";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView packageName;
        final TextView badge;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.item_icon);
            label = itemView.findViewById(R.id.item_label);
            packageName = itemView.findViewById(R.id.item_package);
            badge = itemView.findViewById(R.id.item_badge);
        }
    }
}
