package com.setren.lineageenhance;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class WakeBrightnessRepair {
    private static WakeBrightnessRepair active;
    private final DisplayManager display;
    private final PowerManager power;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean overriding;
    private final Runnable release = this::release;
    private final Runnable pulse = this::pulse;

    static void install(ClassLoader loader) {
        // Application.onCreate is empty and may be inlined out of SystemUI's startup.
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (active == null) active = new WakeBrightnessRepair((Context) param.thisObject);
            }
        });
        try {
            Class<?> controller = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.phone.BiometricUnlockController", loader);
            XposedBridge.hookAllMethods(controller, "startWakeAndUnlock", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable() && active != null) active.request();
                }
            });
        } catch (Throwable error) { log("Biometric callback unavailable", error); }
    }

    private WakeBrightnessRepair(Context context) {
        display = context.getSystemService(DisplayManager.class);
        power = context.getSystemService(PowerManager.class);
        IntentFilter events = new IntentFilter(Intent.ACTION_SCREEN_ON);
        events.addAction(Intent.ACTION_SCREEN_OFF);
        events.addAction(Intent.ACTION_USER_PRESENT);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    handler.removeCallbacks(pulse);
                    handler.removeCallbacks(release);
                    release();
                } else {
                    request();
                }
            }
        }, events);
        XposedBridge.log("LineageEnhance: wake brightness repair registered (attach + biometric)");
    }

    private void request() {
        handler.removeCallbacks(pulse);
        // Fingerprint illumination / lock-screen brightness overrides must clear first.
        handler.postDelayed(pulse, 180);
    }

    private void pulse() {
        if (!power.isInteractive()) return;
        try {
            float current = ((Number) XposedHelpers.callMethod(display, "getBrightness", 0)).floatValue();
            float level = WakeBrightness.pulse(current);
            if (Float.isNaN(level)) return;
            XposedHelpers.callMethod(display, "setTemporaryBrightness", 0, level);
            overriding = true;
            handler.removeCallbacks(release);
            handler.postDelayed(release, 300);
        } catch (Throwable error) { log("Brightness pulse failed", error); }
    }

    private void release() {
        if (!overriding) return;
        try {
            XposedHelpers.callMethod(display, "setTemporaryBrightness", 0, Float.NaN);
            overriding = false;
        } catch (Throwable error) { log("Cannot clear temporary brightness", error); }
    }

    private static void log(String message, Throwable error) {
        XposedBridge.log("LineageEnhance: " + message);
        XposedBridge.log(error);
    }
}
