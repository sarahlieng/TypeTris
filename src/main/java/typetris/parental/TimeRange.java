package typetris.parental;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Immutable time window with inclusive start/end bounds.
 */
public final class TimeRange {
    private final LocalTime start;
    private final LocalTime end;

    public TimeRange(LocalTime start, LocalTime end) {
        this.start = Objects.requireNonNull(start, "start must not be null");
        this.end = Objects.requireNonNull(end, "end must not be null");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must be on or after start");
        }
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    /**
     * Returns true when {@code value} is within [start, end].
     */
    public boolean contains(LocalTime value) {
        Objects.requireNonNull(value, "value must not be null");
        return !value.isBefore(start) && !value.isAfter(end);
    }
}
