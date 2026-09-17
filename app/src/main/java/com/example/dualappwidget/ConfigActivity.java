package com.example.dualappwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户配置界面：
 * 添加小部件时由桌面拉起（APPWIDGET_CONFIGURE），也可以从桌面图标直接打开。
 * 先选中"上半部分 / 下半部分"，再从应用列表里点一个应用即可。
 */
public class ConfigActivity extends AppCompatActivity implements AppListAdapter.OnAppClickListener {

    private static final int SLOT_TOP = 0;
    private static final int SLOT_BOTTOM = 1;

    private static final String STATE_TOP = "state_top_package";
    private static final String STATE_BOTTOM = "state_bottom_package";
    private static final String STATE_SLOT = "state_active_slot";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int activeSlot = SLOT_TOP;

    private String topPackage;
    private String bottomPackage;

    private AppListAdapter adapter;
    private ProgressBar loading;

    private LinearLayout slotTop;
    private LinearLayout slotBottom;
    private ImageView topIcon;
    private ImageView bottomIcon;
    private TextView topLabel;
    private TextView bottomLabel;
    private TextView currentSlotHint;

    private final Map<String, AppInfo> appMap = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        // 默认是"取消"：用户中途返回时，桌面不会添加这个还没配置好的小部件
        setResult(RESULT_CANCELED);

        resolveWidgetId();
        bindViews();

        if (savedInstanceState != null) {
            topPackage = savedInstanceState.getString(STATE_TOP);
            bottomPackage = savedInstanceState.getString(STATE_BOTTOM);
            activeSlot = savedInstanceState.getInt(STATE_SLOT, SLOT_TOP);
        } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            topPackage = Prefs.getTop(this, appWidgetId);
            bottomPackage = Prefs.getBottom(this, appWidgetId);
            activeSlot = pickInitialSlot();
        }

        setupRecyclerView();
        setupSlots();
        updateSlotUi();
        loadInstalledApps();
    }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        if (outState == null) {
            return;
        }
        outState.putString(STATE_TOP, topPackage);
        outState.putString(STATE_BOTTOM, bottomPackage);
        outState.putInt(STATE_SLOT, activeSlot);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ 初始化

    private void resolveWidgetId() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID)) {
            appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
    }

    /** 在标题栏标出正在编辑哪个实例，避免多个实例之间搞混 */
    private void showWhichInstance() {
        TextView subtitle = findViewById(R.id.config_subtitle);
        if (subtitle != null) {
            subtitle.setText(getString(R.string.config_subtitle_with_id, appWidgetId));
        }
    }

    private int pickInitialSlot() {
        if (TextUtils.isEmpty(topPackage)) {
            return SLOT_TOP;
        }
        if (TextUtils.isEmpty(bottomPackage)) {
            return SLOT_BOTTOM;
        }
        return SLOT_TOP;
    }

    private void bindViews() {
        loading = findViewById(R.id.loading);
        slotTop = findViewById(R.id.slot_top);
        slotBottom = findViewById(R.id.slot_bottom);
        topIcon = findViewById(R.id.slot_top_icon);
        bottomIcon = findViewById(R.id.slot_bottom_icon);
        topLabel = findViewById(R.id.slot_top_label);
        bottomLabel = findViewById(R.id.slot_bottom_label);
        currentSlotHint = findViewById(R.id.current_slot_hint);
    }

    private void setupRecyclerView() {
        RecyclerView list = findViewById(R.id.app_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new AppListAdapter(this);
        list.setAdapter(adapter);
    }

    private void setupSlots() {
        slotTop.setOnClickListener(v -> {
            activeSlot = SLOT_TOP;
            updateSlotUi();
        });
        slotBottom.setOnClickListener(v -> {
            activeSlot = SLOT_BOTTOM;
            updateSlotUi();
        });
        // 长按清空对应的那一半
        slotTop.setOnLongClickListener(v -> {
            topPackage = null;
            activeSlot = SLOT_TOP;
            updateSlotUi();
            return true;
        });
        slotBottom.setOnLongClickListener(v -> {
            bottomPackage = null;
            activeSlot = SLOT_BOTTOM;
            updateSlotUi();
            return true;
        });

        Button save = findViewById(R.id.btn_save);
        save.setOnClickListener(v -> save());
        Button cancel = findViewById(R.id.btn_cancel);
        cancel.setOnClickListener(v -> finish());
    }

    // ------------------------------------------------------------------ 应用列表

    private void loadInstalledApps() {
        loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            final List<AppInfo> apps = queryLauncherApps();
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                if (apps.isEmpty()) {
                    Toast.makeText(this, R.string.toast_load_failed, Toast.LENGTH_LONG).show();
                }
                appMap.clear();
                for (AppInfo info : apps) {
                    appMap.put(info.getPackageName(), info);
                }
                adapter.submit(apps);
                updateSlotUi();
            });
        });
    }

    /** 用 PackageManager 查出所有带桌面入口的应用，排除自己，按名称排序 */
    private List<AppInfo> queryLauncherApps() {
        List<AppInfo> result = new ArrayList<>();
        try {
            PackageManager pm = getPackageManager();
            Intent mainIntent = new Intent(Intent.ACTION_MAIN);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);
            Map<String, AppInfo> unique = new LinkedHashMap<>();
            String selfPackage = getPackageName();
            for (ResolveInfo info : resolved) {
                if (info == null || info.activityInfo == null) {
                    continue;
                }
                String packageName = info.activityInfo.packageName;
                if (TextUtils.isEmpty(packageName)
                        || selfPackage.equals(packageName)
                        || unique.containsKey(packageName)) {
                    continue;
                }
                CharSequence label;
                Drawable icon;
                try {
                    label = info.loadLabel(pm);
                    icon = info.loadIcon(pm);
                } catch (Exception e) {
                    label = packageName;
                    icon = null;
                }
                unique.put(packageName, new AppInfo(packageName,
                        label == null ? packageName : label.toString(), icon));
            }
            result = new ArrayList<>(unique.values());
            Collator collator = Collator.getInstance(Locale.CHINA);
            Collections.sort(result, (a, b) -> collator.compare(a.getLabel(), b.getLabel()));
        } catch (Exception ignored) {
            // 读不到就给空列表，界面会提示
        }
        return result;
    }

    @Override
    public void onAppClick(AppInfo appInfo) {
        if (appInfo == null) {
            return;
        }
        if (activeSlot == SLOT_TOP) {
            topPackage = appInfo.getPackageName();
            if (TextUtils.isEmpty(bottomPackage)) {
                activeSlot = SLOT_BOTTOM;
            }
        } else {
            bottomPackage = appInfo.getPackageName();
            if (TextUtils.isEmpty(topPackage)) {
                activeSlot = SLOT_TOP;
            }
        }
        updateSlotUi();
    }

    // ------------------------------------------------------------------ 界面刷新

    private void updateSlotUi() {
        slotTop.setSelected(activeSlot == SLOT_TOP);
        slotBottom.setSelected(activeSlot == SLOT_BOTTOM);
        currentSlotHint.setText(activeSlot == SLOT_TOP
                ? R.string.current_slot_hint_top
                : R.string.current_slot_hint_bottom);

        bindSlot(topIcon, topLabel, topPackage);
        bindSlot(bottomIcon, bottomLabel, bottomPackage);

        if (adapter != null) {
            adapter.setSelection(topPackage, bottomPackage);
        }
    }

    private void bindSlot(ImageView icon, TextView label, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            icon.setImageResource(R.drawable.ic_placeholder);
            label.setText(R.string.not_selected);
            return;
        }
        AppInfo info = appMap.get(packageName);
        if (info != null && info.getIcon() != null) {
            icon.setImageDrawable(info.getIcon());
        } else {
            icon.setImageResource(R.drawable.ic_placeholder);
        }
        if (info != null) {
            label.setText(info.getLabel());
        } else {
            try {
                PackageManager pm = getPackageManager();
                label.setText(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)));
            } catch (Exception e) {
                label.setText(packageName);
            }
        }
    }

    // ------------------------------------------------------------------ 保存

    private void save() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.toast_no_widget, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Prefs.setTop(this, appWidgetId, topPackage);
        Prefs.setBottom(this, appWidgetId, bottomPackage);

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        manager.updateAppWidget(appWidgetId, DualAppWidgetProvider.buildViews(this, appWidgetId));

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
