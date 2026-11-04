package com.setren.lineageenhance;

/** One screen-off session; time is elapsed realtime so sleep counts toward the deadline. */
final class AodSession {
    private long deadline = -1;
    private boolean expired;

    static long parseSeconds(String text) {
        long seconds = Long.parseLong(text.trim());
        if (seconds < 0 || seconds > Integer.MAX_VALUE) throw new IllegalArgumentException();
        return seconds;
    }

    long enterAod(long now, long seconds) {
        if (deadline < 0 && !expired && seconds > 0 && seconds <= Integer.MAX_VALUE) {
            deadline = Math.addExact(now, seconds * 1000L);
        }
        return deadline;
    }

    boolean expire(long now) {
        if (deadline >= 0 && now >= deadline) expired = true;
        return expired;
    }

    boolean blocks(String state) {
        return expired && state.startsWith("DOZE_AOD");
    }

    void reset() {
        deadline = -1;
        expired = false;
    }
}
