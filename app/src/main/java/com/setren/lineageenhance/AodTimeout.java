package com.setren.lineageenhance;

import android.app.AlarmManager;
import android.app.AndroidAppHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class AodTimeout {
    private static final String SESSION = "com.setren.lineageenhance.aod";
    private final Object machine;
    private final Object doze;
    private final Context context;
    private final AlarmManager alarms;
    private final PowerManager power;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AodSession session = new AodSession();
    private final long seconds;
    private boolean scheduled;
    private boolean registered;
    private final AlarmManager.OnAlarmListener timeout = this::onTimeout;
    private final Runnable timeoutWhileAwake = this::onTimeout;
    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { reset(); }
    };

    static void install(ClassLoader loader, long seconds) {
        Class<?> machine = XposedHelpers.findClass("com.android.systemui.doze.DozeMachine", loader);
        Class<?> state = XposedHelpers.findClass("com.android.systemui.doze.DozeMachine$State", loader);
        Object doze = XposedHelpers.getStaticObjectField(state, "DOZE");
        // R8 has inlined transitionPolicy on fuxi; transitionTo remains the shared state entry.
        XposedHelpers.findAndHookMethod(machine, "transitionTo", state, int.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    AodTimeout controller = (AodTimeout) XposedHelpers.getAdditionalInstanceField(param.thisObject, SESSION);
                    if (controller == null) {
                        controller = new AodTimeout(param.thisObject, doze, seconds);
                        XposedHelpers.setAdditionalInstanceField(param.thisObject, SESSION, controller);
                    }
                    String requested = ((Enum<?>) param.args[0]).name();
                    if ("INITIALIZED".equals(requested)) controller.begin();
                    controller.session.expire(SystemClock.elapsedRealtime());
                    if (controller.session.blocks(requested)) param.args[0] = doze;
                } catch (Throwable error) { log("state hook failed", error); }
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) return;
                try {
                    AodTimeout controller = (AodTimeout) XposedHelpers.getAdditionalInstanceField(param.thisObject, SESSION);
                    if (controller != null) controller.onState();
                } catch (Throwable error) { log("state update failed", error); }
            }
        });
        XposedBridge.log("LineageEnhance: AOD timeout installed, seconds=" + seconds);
    }

    private AodTimeout(Object machine, Object doze, long seconds) {
        this.machine = machine;
        this.doze = doze;
        this.seconds = seconds;
        context = AndroidAppHelper.currentApplication();
        if (context == null) throw new IllegalStateException("SystemUI application unavailable");
        alarms = context.getSystemService(AlarmManager.class);
        power = context.getSystemService(PowerManager.class);
    }

    private void begin() {
        reset();
        if (!registered) {
            context.registerReceiver(wake, new IntentFilter(Intent.ACTION_SCREEN_ON));
            registered = true;
        }
    }

    private void onState() {
        String state = state();
        if ("FINISH".equals(state)) {
            reset();
            if (registered) {
                context.unregisterReceiver(wake);
                registered = false;
            }
            return;
        }
        // AOD can be entered before PowerManager finishes switching to non-interactive.
        if (!state.startsWith("DOZE_AOD")) return;
        if (session.expire(SystemClock.elapsedRealtime())) {
            // Post outside transitionTo so requestState owns its normal transition wake lock.
            handler.post(this::closeAod);
            return;
        }
        long deadline = session.enterAod(SystemClock.elapsedRealtime(), seconds);
        if (!scheduled && deadline >= 0) {
            alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline,
                    "LineageEnhance:AodTimeout", timeout, handler);
            scheduled = true;
            // AlarmManager clamps short alarms to 5s; finish earlier while the CPU is still awake.
            handler.postDelayed(timeoutWhileAwake, Math.max(0, deadline - SystemClock.elapsedRealtime()));
        }
    }

    private void onTimeout() {
        if (scheduled) alarms.cancel(timeout);
        handler.removeCallbacks(timeoutWhileAwake);
        scheduled = false;
        try {
            if (power.isInteractive()) { reset(); return; }
            if (session.expire(SystemClock.elapsedRealtime())) closeAod();
        } catch (Throwable error) { log("timeout failed", error); }
    }

    private void closeAod() {
        try {
            if (!power.isInteractive() && session.blocks(state())) {
                XposedHelpers.callMethod(machine, "requestState", doze);
            }
        } catch (Throwable error) { log("cannot exit AOD", error); }
    }

    private String state() {
        return ((Enum<?>) XposedHelpers.getObjectField(machine, "mState")).name();
    }

    private void reset() {
        if (scheduled) alarms.cancel(timeout);
        scheduled = false;
        handler.removeCallbacksAndMessages(null);
        session.reset();
    }

    private static void log(String message, Throwable error) {
        XposedBridge.log("LineageEnhance AOD: " + message);
        XposedBridge.log(error);
    }
}
