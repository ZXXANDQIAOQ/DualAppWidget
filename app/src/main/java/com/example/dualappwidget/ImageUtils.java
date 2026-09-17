package com.example.dualappwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * 图标 / 尺寸相关的小工具。
 * RemoteViews 只能传 Bitmap，不能直接传 Drawable，所以需要转换。
 */
public final class ImageUtils {

    /** 单个图标在 RemoteViews 里的最大边长，避免 Binder 传输超限（1MB） */
    private static final int MAX_ICON_PX = 168;

    private ImageUtils() {
    }

    public static int dpToPx(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** 小部件里图标的推荐像素尺寸 */
    public static int widgetIconSizePx(Context context) {
        int size = dpToPx(context, 56f);
        return Math.min(size, MAX_ICON_PX);
    }

    public static Bitmap loadAppIcon(Context context, String packageName, int sizePx) {
        try {
            Drawable drawable = context.getPackageManager().getApplicationIcon(packageName);
            return drawableToBitmap(drawable, sizePx);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap drawableToBitmap(Drawable drawable, int sizePx) {
        if (drawable == null || sizePx <= 0) {
            return null;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, sizePx, sizePx);
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}
