package typetris.parental;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Tracks allowed play windows and time-based usage statistics
 *
 * @author CS2212 Group 26, Western University
 */
public final class ParentalControl {
    private final List<TimeRange> allowedPlayWindows = new ArrayList<>();
    private final List<Duration> sessionDurations = new ArrayList<>();

    /**
     * Adds a permitted play window
     *
     * @param range allowed hours; must not be {@code null}
     */
    public void addAllowedPlayWindow(TimeRange range) {
        allowedPlayWindows.add(Objects.requireNonNull(range, "range must not be null"));
    }

    /** Removes every configured play window (play is unrestricted until new windows are added) */
    public void clearAllowedPlayWindows() {
        allowedPlayWindows.clear();
    }

    /**
     * @return an unmodifiable list of configured play windows
     */
    public List<TimeRange> getAllowedPlayWindows() {
        return Collections.unmodifiableList(allowedPlayWindows);
    }

    /**
     * @return {@code true} if no windows are configured or the current clock time falls in a window
     */
    public boolean isPlayAllowedNow() {
        return isPlayAllowedAt(LocalTime.now());
    }

    /**
     * @param time clock time to evaluate
     * @return {@code true} if no windows are configured or {@code time} lies in a window
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
     * Records a completed session length
     *
     * @param sessionDuration non-negative duration
     * @throws IllegalArgumentException if {@code sessionDuration} is negative
     */
    public void recordSession(Duration sessionDuration) {
        Duration safeDuration = Objects.requireNonNull(sessionDuration, "sessionDuration must not be null");
        if (safeDuration.isNegative()) {
            throw new IllegalArgumentException("sessionDuration must be non-negative");
        }
        sessionDurations.add(safeDuration);
    }

    /**
     * Records a session as the duration between two instants
     *
     * @param startedAt session start
     * @param endedAt session end (must not be before {@code startedAt})
     * @throws IllegalArgumentException if {@code endedAt} is before {@code startedAt}
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

    /**
     * @return how many sessions have been recorded
     */
    public int getSessionCount() {
        return sessionDurations.size();
    }

    /**
     * @return sum of all recorded session lengths
     */
    public Duration getTotalSessionTime() {
        Duration total = Duration.ZERO;
        for (Duration duration : sessionDurations) {
            total = total.plus(duration);
        }
        return total;
    }

    /**
     * @return mean session length, or zero when no sessions have been recorded
     */
    public Duration getAverageSessionTime() {
        if (sessionDurations.isEmpty()) {
            return Duration.ZERO;
        }
        return getTotalSessionTime().dividedBy(sessionDurations.size());
    }

    /**
     * @return an unmodifiable list of each recorded session duration
     */
    public List<Duration> getSessionDurations() {
        return Collections.unmodifiableList(sessionDurations);
    }
}
