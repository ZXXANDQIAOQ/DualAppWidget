package com.example.dualappwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 小部件实例管理页（应用的启动入口）。
 *
 * 桌面上可以有任意多个本小部件的实例，每个实例是一组「上/下」配置，
 * 这里把它们全列出来，可以逐个进配置页修改；也可以用「添加新实例」直接向桌面申请再加一个。
 */
public class WidgetListActivity extends AppCompatActivity {

    private static final String TAG = "DualAppWidget";

    private AppWidgetManager appWidgetManager;
    private ComponentName provider;

    private LinearLayout container;
    private TextView emptyView;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_list);

        appWidgetManager = AppWidgetManager.getInstance(this);
        provider = new ComponentName(this, DualAppWidgetProvider.class);

        container = findViewById(R.id.instance_container);
        emptyView = findViewById(R.id.manager_empty);
        findViewById(R.id.btn_add_instance).setOnClickListener(v -> requestNewInstance());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从配置页返回、或桌面加了新实例回来时，重新读一遍
        refresh();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ 列表

    private void refresh() {
        container.removeAllViews();

        int[] ids = appWidgetManager.getAppWidgetIds(provider);
        if (ids == null) {
            ids = new int[0];
        }
        emptyView.setVisibility(ids.length == 0 ? View.VISIBLE : View.GONE);
        if (ids.length == 0) {
            return;
        }

        final int[] widgetIds = ids;
        executor.execute(() -> {
            final List<InstanceItem> items = buildItems(widgetIds);
            runOnUiThread(() -> render(items));
        });
    }

    /** 读每个实例的配置，并解析出应用名和图标（放后台线程做） */
    private List<InstanceItem> buildItems(int[] widgetIds) {
        List<InstanceItem> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (int widgetId : widgetIds) {
            InstanceItem item = new InstanceItem();
            item.widgetId = widgetId;
            item.topPackage = Prefs.getTop(this, widgetId);
            item.bottomPackage = Prefs.getBottom(this, widgetId);
            item.topLabel = loadLabel(pm, item.topPackage);
            item.bottomLabel = loadLabel(pm, item.bottomPackage);
            item.topIcon = loadIcon(pm, item.topPackage);
            item.bottomIcon = loadIcon(pm, item.bottomPackage);
            items.add(item);
        }
        return items;
    }

    private void render(List<InstanceItem> items) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int index = 1;
        for (InstanceItem item : items) {
            View row = inflater.inflate(R.layout.item_widget_instance, container, false);

            ImageView topIcon = row.findViewById(R.id.instance_top_icon);
            ImageView bottomIcon = row.findViewById(R.id.instance_bottom_icon);
            if (item.topIcon != null) {
                topIcon.setImageDrawable(item.topIcon);
            } else {
                topIcon.setImageResource(R.drawable.ic_placeholder);
            }
            if (item.bottomIcon != null) {
                bottomIcon.setImageDrawable(item.bottomIcon);
            } else {
                bottomIcon.setImageResource(R.drawable.ic_placeholder);
            }

            TextView title = row.findViewById(R.id.instance_title);
            title.setText(getString(R.string.instance_title, index));

            TextView summary = row.findViewById(R.id.instance_summary);
            String top = TextUtils.isEmpty(item.topPackage)
                    ? getString(R.string.manager_no_app) : item.topLabel;
            String bottom = TextUtils.isEmpty(item.bottomPackage)
                    ? getString(R.string.manager_no_app) : item.bottomLabel;
            summary.setText(getString(R.string.instance_summary, top, bottom));

            final int widgetId = item.widgetId;
            row.findViewById(R.id.instance_edit).setOnClickListener(v -> openConfig(widgetId));

            container.addView(row);
            index++;
        }
    }

    private String loadLabel(PackageManager pm, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            // 已卸载或者查不到，退化成包名
            return packageName;
        }
    }

    private Drawable loadIcon(PackageManager pm, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            return pm.getApplicationIcon(packageName);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 操作

    private void openConfig(int widgetId) {
        Intent intent = new Intent(this, ConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        startActivity(intent);
    }

    /**
     * 添加一个新实例。
     *
     * 实测（澎湃 OS 4.0 V816）：桌面不接受第三方应用发起的 pin 请求，
     * requestPinAppWidget 会被系统接受、但桌面立刻拒绝
     * （桌面日志：setup_widget abort: permission denied package=...），
     * 系统标准 pin 接口在本机是条死路，所以这里直接给出可照做的手动步骤。
     */
    private void requestNewInstance() {
        boolean requested = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && appWidgetManager.isRequestPinAppWidgetSupported()) {
            // 桌面（澎湃 OS）的 pin 流程会把请求转给「小部件详情页」，
            // 而它读的是 requestPinAppWidget 的 extras —— 传 null 会被直接丢弃
            // （桌面日志：start_widget_detail_page return false: extras is None）。
            // 另外 provider 必须是米系小部件身份，桌面才认这个详情页。
            Bundle extras = new Bundle();
            extras.putString("package", getPackageName());
            extras.putString("provider", provider.flattenToString());
            extras.putString("widgetProviderName", provider.flattenToString());
            try {
                requested = appWidgetManager.requestPinAppWidget(provider, extras, null);
                Log.i(TAG, "requestPinAppWidget -> " + requested);
            } catch (Throwable t) {
                Log.e(TAG, "requestPinAppWidget failed", t);
            }
        }

        if (requested) {
            // 桌面接下了请求，会自己弹出添加/确认界面，这里不再叠加自己的对话框
            Toast.makeText(this, R.string.toast_pin_requested, Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_steps_title)
                .setMessage(R.string.add_steps_body)
                .setPositiveButton(R.string.action_got_it, null)
                .show();
    }

    /** 列表一行的数据 */
    private static final class InstanceItem {
        int widgetId;
        String topPackage;
        String bottomPackage;
        String topLabel;
        String bottomLabel;
        Drawable topIcon;
        Drawable bottomIcon;
    }
}
