package com.setren.lineageenhance;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakeBrightnessTest {
    @Test public void pulsesOnlyValidLowBrightness() {
        assertEquals(0.02f, WakeBrightness.pulse(0f), 0f);
        assertEquals(0.02f, WakeBrightness.pulse(0.01f), 0f);
        assertTrue(Float.isNaN(WakeBrightness.pulse(0.3f)));
        assertTrue(Float.isNaN(WakeBrightness.pulse(-1f)));
        assertTrue(Float.isNaN(WakeBrightness.pulse(Float.NaN)));
        assertTrue(Float.isNaN(WakeBrightness.pulse(Float.POSITIVE_INFINITY)));
    }
}
