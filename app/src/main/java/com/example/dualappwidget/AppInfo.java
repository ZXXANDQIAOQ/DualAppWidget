package com.example.dualappwidget;

import android.graphics.drawable.Drawable;

/**
 * 应用信息模型：包名 + 显示名 + 图标。
 */
public class AppInfo {

    private final String packageName;
    private final String label;
    private final Drawable icon;

    public AppInfo(String packageName, String label, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getLabel() {
        return label == null || label.length() == 0 ? packageName : label;
    }

    public Drawable getIcon() {
        return icon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppInfo)) {
            return false;
        }
        return packageName != null && packageName.equals(((AppInfo) o).packageName);
    }

    @Override
    public int hashCode() {
        return packageName == null ? 0 : packageName.hashCode();
    }
}
