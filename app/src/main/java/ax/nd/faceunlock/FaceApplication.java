package ax.nd.faceunlock;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import ax.nd.faceunlock.pref.Prefs;
import ax.nd.faceunlock.service.FacePPPreloader;
import ax.nd.faceunlock.util.NotificationUtils;
import ax.nd.faceunlock.util.Util;
import com.google.android.material.color.DynamicColors;

public class FaceApplication extends Application {
    private static final String TAG = FaceApplication.class.getSimpleName();
    public static FaceApplication mApp;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Util.DEBUG) {
                Log.d(FaceApplication.TAG, "mReceiver Received intent with action = " + action);
            }
            if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                NotificationUtils.checkAndShowNotification(context);
            }
        }
    };
    private FacePPPreloader facePreloader;
    private Prefs prefs;
    private SharedPreferences featurePreferences;
    private boolean featurePreferencesShared;

    public SharedPreferences getFeaturePreferences() { return featurePreferences; }
    public boolean areFeaturePreferencesShared() { return featurePreferencesShared; }

    private void initializeFeaturePreferences() {
        try {
            // The first open fixes SharedPreferencesImpl's mode; do this before face preloading.
            featurePreferences = getSharedPreferences("features", Context.MODE_WORLD_READABLE);
            // Migrate the old private file by forcing its first write with the shared mode.
            featurePreferencesShared = featurePreferences.edit().putInt("shared_format", 1).commit();
            if (!featurePreferencesShared) Log.e(TAG, "Cannot save shared feature switches");
        } catch (SecurityException moduleInactive) {
            featurePreferences = getSharedPreferences("features", Context.MODE_PRIVATE);
            featurePreferencesShared = false;
        }
    }

    public static FaceApplication getApp() {
        return mApp;
    }

    public FacePPPreloader getFacePreloader() {
        return facePreloader;
    }

    public Prefs getPrefs() {
        return prefs;
    }

    @Override
    public void onCreate() {
        if (Util.DEBUG) {
            Log.d(TAG, "onCreate");
        }
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        mApp = this;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mReceiver, intentFilter, null, null);
        getPackageManager().setComponentEnabledSetting(new ComponentName(this, SetupFaceIntroActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        initializeFeaturePreferences();
        if (featurePreferences.getBoolean("face", false)) {
            Util.setFaceUnlockAvailable(getApplicationContext());
        }
        LibManager.INSTANCE.init(this);
        prefs = new Prefs(this);
        facePreloader = new FacePPPreloader(this);
    }

    @Override
    public void onTerminate() {
        if (Util.DEBUG) {
            Log.d(TAG, "onTerminate");
        }
        super.onTerminate();
    }

    public void postRunnable(Runnable runnable) {
        mHandler.post(runnable);
    }
}
