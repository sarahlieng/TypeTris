package typetris.parental;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Immutable time window with inclusive start/end bounds
 *
 * @author CS2212 Group 26, Western University
 */
public final class TimeRange {
    private final LocalTime start;
    private final LocalTime end;

    /**
     * Creates a range from {@code start} through {@code end} inclusive
     *
     * @param start inclusive start time
     * @param end inclusive end time (must not be before {@code start})
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public TimeRange(LocalTime start, LocalTime end) {
        this.start = Objects.requireNonNull(start, "start must not be null");
        this.end = Objects.requireNonNull(end, "end must not be null");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must be on or after start");
        }
    }

    /**
     * @return the inclusive start of this range
     */
    public LocalTime getStart() {
        return start;
    }

    /**
     * @return the inclusive end of this range
     */
    public LocalTime getEnd() {
        return end;
    }

    /**
     * Returns whether {@code value} lies between start and end inclusive
     *
     * @param value time to test
     * @return {@code true} if {@code value} is in this range
     */
    public boolean contains(LocalTime value) {
        Objects.requireNonNull(value, "value must not be null");
        return !value.isBefore(start) && !value.isAfter(end);
    }
}
