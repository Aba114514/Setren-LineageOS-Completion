package com.setren.recentslock;

import android.content.Context;
import android.content.SharedPreferences;
import android.animation.ObjectAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.Property;
import android.view.View;
import android.view.Gravity;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Hook implements IXposedHookLoadPackage {
    private static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final Set<Integer> locked = new HashSet<>();
    private static SharedPreferences prefs;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        boolean trebuchet = "com.android.launcher3".equals(load.packageName);
        if (!LAUNCHER.equals(load.packageName) && !trebuchet) return;
        ClassLoader cl = load.classLoader;
        Class<?> launch = XposedHelpers.findClass("com.android.launcher3.uioverrides.touchcontrollers.TaskViewLaunchTouchController", cl);
        XposedHelpers.findAndHookMethod(launch, "onDragStart", boolean.class, float.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                FrameLayout task = (FrameLayout) XposedHelpers.getObjectField(p.thisObject, "taskBeingDragged");
                if (task == null || !(Boolean) XposedHelpers.callMethod(task, "isRunningTask")) return;
                Object recents = XposedHelpers.callMethod(task, "getRecentsView");
                if (recents != null) XposedHelpers.callMethod(recents, "switchToScreenshot", (Runnable) () ->
                        XposedHelpers.callMethod(recents, "finishRecentsAnimation", true, false, (Runnable) () -> {}));
            }
        });
        XposedHelpers.findAndHookMethod(launch, "onDrag", float.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                FrameLayout task = (FrameLayout) XposedHelpers.getObjectField(p.thisObject, "taskBeingDragged");
                if (task == null || XposedHelpers.getObjectField(p.thisObject, "playbackController") == null) return;
                float end = XposedHelpers.getFloatField(p.thisObject, "launchEndDisplacement");
                float movement = Math.max(0f, (Float) p.args[0] * Math.signum(end));
                float limit = travelLimit(task);
                float edge = limit * 0.7f;
                float distance = movement <= edge ? movement
                        : edge + (limit - edge) * (1f - (float) Math.exp(-(movement - edge) / (limit - edge)));
                XposedHelpers.callMethod(task, "setBeingDraggedForDismissal", true);
                Property shift = (Property) XposedHelpers.callMethod(task, "getSecondaryDismissTranslationProperty");
                shift.set(task, distance);
                redraw(task);
                p.setResult(true);
            }
        });
        XposedHelpers.findAndHookMethod(launch, "onDragEnd", float.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                FrameLayout task = (FrameLayout) XposedHelpers.getObjectField(p.thisObject, "taskBeingDragged");
                if (task == null || XposedHelpers.getObjectField(p.thisObject, "playbackController") == null) return;
                Property shift = (Property) XposedHelpers.callMethod(task, "getSecondaryDismissTranslationProperty");
                float distance = ((Number) shift.get(task)).floatValue();
                float velocity = (Float) p.args[0];
                boolean fling = (Boolean) XposedHelpers.callMethod(XposedHelpers.getObjectField(p.thisObject, "detector"), "isFling", velocity);
                p.setObjectExtra("animatedTask", task);
                if (distance > travelLimit(task) * 0.55f || (fling && velocity > 0)) {
                    int[] ids = (int[]) XposedHelpers.callMethod(task, "getTaskIds");
                    if (ids.length != 0) {
                        Context context = (Context) XposedHelpers.getObjectField(p.thisObject, "container");
                        init(context);
                        boolean wasLocked = locked.contains(ids[0]);
                        for (int id : ids) {
                            if (wasLocked) locked.remove(id); else locked.add(id);
                        }
                        save();
                        XposedBridge.log("RecentsLock: " + (wasLocked ? "unlock " : "lock ") + java.util.Arrays.toString(ids));
                        badge(task);
                        Toast.makeText(context, wasLocked ? "最近任务已解锁" : "最近任务已锁定", Toast.LENGTH_SHORT).show();
                    }
                }
                // Native launch playback stays at zero; only the card follows the finger.
                p.args[0] = 0f;
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                FrameLayout task = (FrameLayout) p.getObjectExtra("animatedTask");
                if (task != null) settle(task);
            }
        });

        Class<?> taskView = XposedHelpers.findClass("com.android.quickstep.views.TaskView", cl);
        XposedHelpers.findAndHookMethod(taskView, "setTaskContainers", List.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                init(((View) p.thisObject).getContext());
                badge((FrameLayout) p.thisObject);
            }
        });

        Class<?> dismiss = XposedHelpers.findClass("com.android.quickstep.views.RecentsDismissUtils", cl);
        XposedHelpers.findAndHookMethod(dismiss, "dismissAllTasks", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object recents = XposedHelpers.getObjectField(p.thisObject, "recentsView");
                Context context = ((View) recents).getContext();
                init(context);
                Iterable<?> views = (Iterable<?>) XposedHelpers.callMethod(recents, "getTaskViews");
                List<Object> remove = new ArrayList<>();
                boolean hasLocked = false;
                for (Object view : views) {
                    int[] ids = (int[]) XposedHelpers.callMethod(view, "getTaskIds");
                    boolean keep = false;
                    for (int id : ids) if (locked.contains(id)) keep = true;
                    if (keep) hasLocked = true; else remove.add(view);
                }
                XposedBridge.log("RecentsLock: clear all, protected=" + hasLocked + ", remove=" + remove.size());
                if (!hasLocked) return;
                p.setResult(null);
                View host = (View) recents;
                for (int i = 0; i < remove.size(); i++) {
                    Object task = remove.get(i);
                    host.postDelayed(() -> {
                        if (trebuchet) XposedHelpers.callMethod(recents, "dismissTaskView", task, true);
                        else XposedHelpers.callMethod(recents, "dismissTaskView", task, true, true);
                    }, i * 350L);
                }
            }
        });
        XposedBridge.log("RecentsLock: hooks active for " + load.packageName);
    }

    private static void init(Context context) {
        if (prefs != null) return;
        prefs = context.getSharedPreferences("recents_lock", Context.MODE_PRIVATE);
        int boot = android.provider.Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        if (prefs.getInt("boot", -2) != boot) {
            prefs.edit().clear().putInt("boot", boot).apply();
            return;
        }
        for (String id : prefs.getStringSet("tasks", java.util.Collections.emptySet())) {
            try { locked.add(Integer.parseInt(id)); } catch (NumberFormatException ignored) {}
        }
    }

    private static void save() {
        Set<String> ids = new HashSet<>();
        for (int id : locked) ids.add(Integer.toString(id));
        prefs.edit().putStringSet("tasks", ids).apply();
    }

    private static void badge(FrameLayout task) {
        ImageView icon = (ImageView) task.findViewWithTag("recents_lock_badge");
        boolean keep = false;
        for (int id : (int[]) XposedHelpers.callMethod(task, "getTaskIds")) if (locked.contains(id)) keep = true;
        if (icon == null && keep) {
            String packageName = task.getContext().getPackageName();
            int headerId = task.getResources().getIdentifier("icon", "id", packageName);
            int titleId = task.getResources().getIdentifier("icon_title", "id", packageName);
            int arrowId = task.getResources().getIdentifier("icon_arrow", "id", packageName);
            FrameLayout header = task.findViewById(headerId);
            TextView title = task.findViewById(titleId);
            View arrow = task.findViewById(arrowId);
            if (header == null || title == null) return;
            icon = new ImageView(task.getContext());
            icon.setTag("recents_lock_badge");
            icon.setImageResource(android.R.drawable.ic_lock_lock);
            icon.setColorFilter(0xffffffff);
            icon.setContentDescription("最近任务已锁定");
            float density = task.getResources().getDisplayMetrics().density;
            int size = (int) (18 * density);
            int gap = (int) (6 * density);
            FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(size, size, Gravity.START | Gravity.CENTER_VERTICAL);
            header.addView(icon, position);
            ImageView badge = icon;
            header.post(() -> {
                int left = title.getLeft() + (int) Math.ceil(title.getPaint().measureText(title.getText().toString())) + gap;
                int limit = (arrow == null ? header.getWidth() : arrow.getLeft()) - size - gap;
                position.leftMargin = Math.min(left, limit);
                badge.setLayoutParams(position);
                badge.bringToFront();
            });
        }
        if (icon != null) {
            icon.setVisibility(keep ? View.VISIBLE : View.GONE);
            if (keep) {
                icon.setElevation(8 * task.getResources().getDisplayMetrics().density);
                icon.bringToFront();
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void settle(FrameLayout task) {
        Property shift = (Property) XposedHelpers.callMethod(task, "getSecondaryDismissTranslationProperty");
        ObjectAnimator back = ObjectAnimator.ofFloat(task, shift, 0f);
        back.setDuration(280);
        back.setInterpolator(new OvershootInterpolator(0.7f));
        back.addUpdateListener(animation -> redraw(task));
        back.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                XposedHelpers.callMethod(task, "setBeingDraggedForDismissal", false);
                shift.set(task, 0f);
                redraw(task);
            }
        });
        back.start();
    }

    private static void redraw(FrameLayout task) {
        if (!(Boolean) XposedHelpers.callMethod(task, "isRunningTask")) return;
        Object recents = XposedHelpers.callMethod(task, "getRecentsView");
        if (recents != null) XposedHelpers.callMethod(recents, "redrawLiveTile");
    }

    private static float travelLimit(FrameLayout task) {
        float density = task.getResources().getDisplayMetrics().density;
        return Math.min(task.getHeight() * 0.22f, 180f * density);
    }
}
