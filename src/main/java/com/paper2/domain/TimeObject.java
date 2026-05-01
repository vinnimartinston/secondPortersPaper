package com.paper2.domain;

import java.util.Locale;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Time value in seconds (e.g. instant since midnight). */
@AllArgsConstructor
@Getter
@Setter
public class TimeObject implements Comparable<TimeObject> {
    private int seconds;

    /** Adds seconds; returns a new {@link TimeObject} (immutable with respect to this one). */
    public TimeObject addTime(TimeObject other) {
        int delta = other == null ? 0 : other.seconds;
        return new TimeObject(this.seconds + delta);
    }
    
    public TimeObject addSeconds(int delta) {
        return new TimeObject(this.seconds + delta);
    }

    public TimeObject subtractTime(TimeObject other) {
        int delta = other == null ? 0 : other.seconds;
        return new TimeObject(this.seconds - delta);
    }

    /**
     * Formats {@link #seconds} (since midnight or duration in seconds) as {@code HH:MM:SS}.
     */
    @Override
    public String toString() {
        int total = Math.max(0, this.seconds);
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    public boolean isAtMost(TimeObject other) {
        return this.getSeconds() <= other.getSeconds();
    }

    @Override
    public int compareTo(TimeObject o) {
        if (o == null) {
            return 1;
        }
        return Integer.compare(this.seconds, o.seconds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TimeObject that = (TimeObject) obj;
        return this.seconds == that.seconds;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(seconds);
    }
}
