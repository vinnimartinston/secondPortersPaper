package com.paper2.metrics;

import java.util.Locale;

import com.paper2.domain.TimeObject;

/** Formats durations in seconds as {@code HH:MM:SS} (aligned with {@link TimeObject#toString()} for int). */
public final class ScheduleTimeFormat {

    private ScheduleTimeFormat() {}

    public static String durationHms(int seconds) {
        return new TimeObject(Math.max(0, seconds)).toString();
    }

    public static String durationHmsSigned(int seconds) {
        if (seconds >= 0) {
            return durationHms(seconds);
        }
        return "-" + durationHms(-seconds);
    }

    /** Aggregated sums (e.g. several porters); hours without a two-digit cap. */
    public static String durationHmsUnsignedLong(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "00:00:00";
        }
        long s = totalSeconds;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
    }

    public static String durationHmsSignedLong(long totalSeconds) {
        if (totalSeconds >= 0) {
            return durationHmsUnsignedLong(totalSeconds);
        }
        return "-" + durationHmsUnsignedLong(-totalSeconds);
    }
}
