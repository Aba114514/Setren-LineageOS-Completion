package com.setren.lineageenhance;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.text.InputType;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import ax.nd.faceunlock.R;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Integration settings only; face templates remain in the recognizer's private storage. */
public final class SettingsActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_lineage_settings);
        ComponentName launcher = new ComponentName(this, getPackageName() + ".Launcher");
        PackageManager packages = getPackageManager();
        MaterialSwitch hideIcon = findViewById(R.id.hide_launcher_icon);
        hideIcon.setChecked(packages.getComponentEnabledSetting(launcher)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        hideIcon.setOnCheckedChangeListener((view, hidden) -> packages.setComponentEnabledSetting(
                launcher, hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP));
        findViewById(R.id.face_settings).setOnClickListener(v ->
                startActivity(new Intent(this, ax.nd.faceunlock.MainActivity.class)));
        findViewById(R.id.aod_system_settings).setOnClickListener(v -> {
            try { startActivity(new Intent("android.settings.AMBIENT_DISPLAY_SETTINGS")); }
            catch (ActivityNotFoundException unavailable) {
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
            }
        });
        SharedPreferences preferences;
        try {
            // LSPosed API 93 shares only this file; never share face authentication preferences.
            ax.nd.faceunlock.FaceApplication app = ax.nd.faceunlock.FaceApplication.getApp();
            if (!app.areFeaturePreferencesShared()) throw new SecurityException("Module inactive");
            preferences = app.getFeaturePreferences();
        } catch (SecurityException unavailable) {
            ((TextView) findViewById(R.id.module_status)).setText(R.string.lineage_unavailable);
            findViewById(R.id.feature_recents).setEnabled(false);
            findViewById(R.id.feature_face).setEnabled(false);
            findViewById(R.id.feature_brightness).setEnabled(false);
            findViewById(R.id.feature_aod).setEnabled(false);
            findViewById(R.id.aod_duration).setEnabled(false);
            return;
        }
        bindSwitch(preferences, R.id.feature_recents, "recents");
        bindSwitch(preferences, R.id.feature_face, "face");
        bindSwitch(preferences, R.id.feature_brightness, "brightness");
        bindSwitch(preferences, R.id.feature_aod, "aod_timeout");
        updateDuration(preferences);
        findViewById(R.id.aod_duration).setOnClickListener(v -> editDuration(preferences));
    }

    private void updateDuration(SharedPreferences prefs) {
        long seconds = prefs.getLong("aod_seconds", 15L);
        ((TextView) findViewById(R.id.aod_duration)).setText(seconds == 0
                ? getString(R.string.lineage_aod_unlimited)
                : getString(R.string.lineage_aod_duration, seconds));
    }

    private void editDuration(SharedPreferences prefs) {
        TextInputLayout field = new TextInputLayout(this);
        field.setHint(getString(R.string.lineage_aod_input));
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        field.setPadding(padding, padding / 2, padding, 0);
        TextInputEditText input = new TextInputEditText(field.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(Long.toString(prefs.getLong("aod_seconds", 15L)));
        field.addView(input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lineage_aod).setView(field)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        long seconds = AodSession.parseSeconds(input.getText().toString());
                        prefs.edit().putLong("aod_seconds", seconds).apply();
                        updateDuration(prefs);
                        dialog.dismiss();
                    } catch (IllegalArgumentException invalid) {
                        field.setError(getString(R.string.lineage_aod_invalid));
                    }
                }));
        dialog.show();
        input.requestFocus();
        input.selectAll();
    }

    private void bindSwitch(SharedPreferences prefs, int id, String key) {
        MaterialSwitch toggle = findViewById(id);
        toggle.setChecked(prefs.getBoolean(key, false));
        toggle.setOnCheckedChangeListener((view, enabled) -> prefs.edit().putBoolean(key, enabled).apply());
    }
}
