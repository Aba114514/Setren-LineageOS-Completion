package com.setren.lineageenhance;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Module implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        boolean systemUi = "com.android.systemui".equals(load.packageName);
        boolean launcher = "com.android.launcher3".equals(load.packageName)
                || "com.google.android.apps.nexuslauncher".equals(load.packageName);
        if (!systemUi && !launcher) return;
        XSharedPreferences prefs = new XSharedPreferences("com.setren.lineageenhance", "features");
        if (launcher && prefs.getBoolean("recents", false)) {
            try { new com.setren.recentslock.Hook().handleLoadPackage(load); }
            catch (Throwable error) { log("Recents hook unavailable", error); }
        }
        if (!systemUi) return;
        // Install independently so an unavailable authentication hook cannot disable screen repair.
        if (prefs.getBoolean("brightness", false)) {
            try { WakeBrightnessRepair.install(load.classLoader); }
            catch (Throwable error) { log("Brightness hook unavailable", error); }
        }
        if (prefs.getBoolean("aod_timeout", false)) {
            try { AodTimeout.install(load.classLoader, prefs.getLong("aod_seconds", 15L)); }
            catch (Throwable error) { log("AOD timeout hook unavailable", error); }
        }
        if (prefs.getBoolean("face", false)) {
            try { new ax.nd.universalauth.xposed.Module().handleLoadPackage(load); }
            catch (Throwable error) { log("Face hook unavailable", error); }
        }
    }

    private static void log(String message, Throwable error) {
        XposedBridge.log("LineageEnhance: " + message);
        XposedBridge.log(error);
    }
}
