package com.example.dualappwidget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置存取。键名按小部件实例区分：
 * 上半部分 top_app_&lt;widgetId&gt;，下半部分 bottom_app_&lt;widgetId&gt;。
 */
public final class Prefs {

    private static final String FILE_NAME = "dual_app_widget_prefs";

    private Prefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public static String keyTop(int appWidgetId) {
        return "top_app_" + appWidgetId;
    }

    public static String keyBottom(int appWidgetId) {
        return "bottom_app_" + appWidgetId;
    }

    public static String getTop(Context context, int appWidgetId) {
        return sp(context).getString(keyTop(appWidgetId), null);
    }

    public static String getBottom(Context context, int appWidgetId) {
        return sp(context).getString(keyBottom(appWidgetId), null);
    }

    public static void setTop(Context context, int appWidgetId, String packageName) {
        put(sp(context), keyTop(appWidgetId), packageName);
    }

    public static void setBottom(Context context, int appWidgetId, String packageName) {
        put(sp(context), keyBottom(appWidgetId), packageName);
    }

    public static void clear(Context context, int appWidgetId) {
        sp(context).edit()
                .remove(keyTop(appWidgetId))
                .remove(keyBottom(appWidgetId))
                .apply();
    }

    private static void put(SharedPreferences sp, String key, String value) {
        SharedPreferences.Editor editor = sp.edit();
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
        editor.apply();
    }
}
