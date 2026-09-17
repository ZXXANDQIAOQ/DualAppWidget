package com.example.dualappwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

/**
 * 小部件本体：
 * onUpdate 时读取 SharedPreferences 里保存的两个包名，
 * 分别给上下两个 ImageView 设置图标和 PendingIntent（点击即启动对应 App）。
 */
public class DualAppWidgetProvider extends AppWidgetProvider {

    /** 打开配置界面的 PendingIntent requestCode 偏移，避免和启动应用的撞车 */
    private static final int CONFIG_REQUEST_OFFSET = 10000;

    /** 小米 Widget 的曝光刷新广播（桌面滑到小部件所在页面时下发） */
    private static final String ACTION_MIUI_APPWIDGET_UPDATE =
            "miui.appwidget.action.APPWIDGET_UPDATE";

    /**
     * 小米 Widget 走的是自己的一条刷新广播，AppWidgetProvider 默认不认它，
     * 必须在 onReceive 里手动接住，否则曝光刷新等于没配。
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_MIUI_APPWIDGET_UPDATE.equals(intent.getAction())) {
            int[] appWidgetIds =
                    intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (appWidgetIds != null && appWidgetIds.length > 0) {
                onUpdate(context, AppWidgetManager.getInstance(context), appWidgetIds);
            } else {
                // 广播没带 id 时退化成全量重刷
                AppWidgetManager manager = AppWidgetManager.getInstance(context);
                onUpdate(context, manager,
                        manager.getAppWidgetIds(new android.content.ComponentName(
                                context, DualAppWidgetProvider.class)));
            }
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null) {
            return;
        }
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId));
        }
    }

    /**
     * 尺寸/位置变化时（手动缩放、被拖进桌面的堆叠位、屏幕旋转等）重新渲染一次。
     * 不重写这个方法也能显示，但桌面调整格子数后不会主动 refresh，容易出现图标被裁切。
     */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        if (appWidgetIds == null) {
            return;
        }
        // 小部件被移除后清掉它的配置，避免 SharedPreferences 里留下垃圾键
        for (int appWidgetId : appWidgetIds) {
            Prefs.clear(context, appWidgetId);
        }
        super.onDeleted(context, appWidgetIds);
    }

    /** 按配置生成 RemoteViews，配置界面保存后也会直接调它刷新 */
    public static RemoteViews buildViews(Context context, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.dual_app_widget);
        boolean topReady = bindSlot(context, views, appWidgetId, true);
        boolean bottomReady = bindSlot(context, views, appWidgetId, false);
        views.setViewVisibility(R.id.widget_hint,
                (topReady && bottomReady) ? View.GONE : View.VISIBLE);
        return views;
    }

    /**
     * 绑定一半的点击区域。
     *
     * @return true 表示这一半已经配置且有可启动的 Intent
     */
    private static boolean bindSlot(Context context, RemoteViews views, int appWidgetId, boolean top) {
        int imageId = top ? R.id.widget_top : R.id.widget_bottom;
        String packageName = top
                ? Prefs.getTop(context, appWidgetId)
                : Prefs.getBottom(context, appWidgetId);

        Intent launchIntent = TextUtils.isEmpty(packageName)
                ? null
                : context.getPackageManager().getLaunchIntentForPackage(packageName);

        if (launchIntent == null) {
            // 没配置，或者配置的应用已被卸载：显示占位图，点击去配置
            views.setImageViewResource(imageId, R.drawable.ic_placeholder);
            views.setOnClickPendingIntent(imageId, configPendingIntent(context, appWidgetId, top));
            return false;
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        Bitmap icon = ImageUtils.loadAppIcon(context, packageName,
                ImageUtils.widgetIconSizePx(context));
        if (icon != null) {
            views.setImageViewBitmap(imageId, icon);
        } else {
            views.setImageViewResource(imageId, R.drawable.ic_placeholder);
        }

        views.setOnClickPendingIntent(imageId,
                launchPendingIntent(context, appWidgetId, top, launchIntent));
        return true;
    }

    private static PendingIntent launchPendingIntent(Context context, int appWidgetId,
                                                     boolean top, Intent launchIntent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, requestCode(appWidgetId, top), launchIntent, flags);
    }

    private static PendingIntent configPendingIntent(Context context, int appWidgetId, boolean top) {
        Intent intent = new Intent(context, ConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context,
                CONFIG_REQUEST_OFFSET + requestCode(appWidgetId, top), intent, flags);
    }

    /** 上下两个区域必须用不同的 requestCode：上 => widgetId*2，下 => widgetId*2+1 */
    private static int requestCode(int appWidgetId, boolean top) {
        return appWidgetId * 2 + (top ? 0 : 1);
    }
}
