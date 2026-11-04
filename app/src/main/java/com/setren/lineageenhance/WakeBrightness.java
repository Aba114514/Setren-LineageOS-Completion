package com.setren.lineageenhance;

final class WakeBrightness {
    private WakeBrightness() {}

    static float pulse(float current) {
        // shortcut: fixed pulse for fuxi, calibrate on hardware before supporting other panels.
        return current >= 0f && current <= 0.01f ? 0.02f : Float.NaN;
    }
}
