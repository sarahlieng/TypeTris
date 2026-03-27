package typetris.parental;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Tracks allowed play windows and time-based usage statistics.
 */
public final class ParentalControl {
    private final List<TimeRange> allowedPlayWindows = new ArrayList<>();
    private final List<Duration> sessionDurations = new ArrayList<>();

    /**
     * Adds a permitted play window.
     */
    public void addAllowedPlayWindow(TimeRange range) {
        allowedPlayWindows.add(Objects.requireNonNull(range, "range must not be null"));
    }

    /**
     * Clears all permitted play windows.
     */
    public void clearAllowedPlayWindows() {
        allowedPlayWindows.clear();
    }

    /**
     * Returns an immutable view of configured play windows.
     */
    public List<TimeRange> getAllowedPlayWindows() {
        return Collections.unmodifiableList(allowedPlayWindows);
    }

    /**
     * Returns true if no windows are configured or current time is inside any window.
     */
    public boolean isPlayAllowedNow() {
        return isPlayAllowedAt(LocalTime.now());
    }

    /**
     * Returns true if no windows are configured or {@code time} is inside any window.
     */
    public boolean isPlayAllowedAt(LocalTime time) {
        Objects.requireNonNull(time, "time must not be null");
        if (allowedPlayWindows.isEmpty()) {
            return true;
        }
        for (TimeRange window : allowedPlayWindows) {
            if (window.contains(time)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records a completed session.
     */
    public void recordSession(Duration sessionDuration) {
        Duration safeDuration = Objects.requireNonNull(sessionDuration, "sessionDuration must not be null");
        if (safeDuration.isNegative()) {
            throw new IllegalArgumentException("sessionDuration must be non-negative");
        }
        sessionDurations.add(safeDuration);
    }

    /**
     * Utility to record a session from start/end timestamps.
     */
    public void recordSession(LocalDateTime startedAt, LocalDateTime endedAt) {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(endedAt, "endedAt must not be null");
        Duration sessionDuration = Duration.between(startedAt, endedAt);
        if (sessionDuration.isNegative()) {
            throw new IllegalArgumentException("endedAt must be on or after startedAt");
        }
        sessionDurations.add(sessionDuration);
    }

    public int getSessionCount() {
        return sessionDurations.size();
    }

    public Duration getTotalSessionTime() {
        Duration total = Duration.ZERO;
        for (Duration duration : sessionDurations) {
            total = total.plus(duration);
        }
        return total;
    }

    /**
     * Returns zero when no sessions have been recorded.
     */
    public Duration getAverageSessionTime() {
        if (sessionDurations.isEmpty()) {
            return Duration.ZERO;
        }
        return getTotalSessionTime().dividedBy(sessionDurations.size());
    }

    /**
     * Returns an immutable view of all recorded session durations.
     */
    public List<Duration> getSessionDurations() {
        return Collections.unmodifiableList(sessionDurations);
    }
}
