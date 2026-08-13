package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restricted semantic-version range used by the plugin marketplace contract.
 */
public final class SemVerRange {

    private static final Pattern NUMBER = Pattern.compile("0|[1-9]\\d*");
    private static final Pattern BOUNDED_RANGE = Pattern.compile(">(=)(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*) <(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

    private final List<Constraint> constraints;

    private SemVerRange(List<Constraint> constraints) {
        this.constraints = List.copyOf(constraints);
    }

    public static SemVerRange parse(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(value);
        }
        if (value.startsWith("^")) {
            return prefixed(value, '^');
        }
        if (value.startsWith("~")) {
            return prefixed(value, '~');
        }
        if (containsWildcard(value)) {
            return wildcard(value);
        }
        if (value.indexOf('^') >= 0 || value.indexOf('~') >= 0) {
            throw invalid(value);
        }
        Matcher boundedRange = BOUNDED_RANGE.matcher(value);
        if (boundedRange.matches()) {
            SemVer lowerBound = version(boundedRange, 2);
            SemVer upperBound = version(boundedRange, 5);
            return bounded(lowerBound, upperBound);
        }
        try {
            return new SemVerRange(List.of(new Constraint(Operator.EQUAL, SemVer.parse(value))));
        } catch (IllegalArgumentException exception) {
            throw invalid(value);
        }
    }

    public boolean matches(SemVer version) {
        return constraints.stream().allMatch(constraint -> constraint.matches(version));
    }

    private static SemVerRange prefixed(String value, char prefix) {
        if (value.length() == 1) {
            throw invalid(value);
        }
        try {
            SemVer version = SemVer.parse(value.substring(1));
            return prefix == '^' ? caret(version) : tilde(version);
        } catch (IllegalArgumentException exception) {
            throw invalid(value);
        }
    }

    private static SemVerRange caret(SemVer version) {
        SemVer upperBound = version.major() > 0 ? new SemVer(increment(version.major()), 0, 0)
                : version.minor() > 0 ? new SemVer(0, increment(version.minor()), 0)
                : new SemVer(0, 0, increment(version.patch()));
        return bounded(version, upperBound);
    }

    private static SemVerRange tilde(SemVer version) {
        return bounded(version, new SemVer(version.major(), increment(version.minor()), 0));
    }

    private static SemVerRange wildcard(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length < 1 || parts.length > 3) {
            throw invalid(value);
        }
        if (isWildcard(parts[0])) {
            for (String part : parts) {
                if (!isWildcard(part)) {
                    throw invalid(value);
                }
            }
            return new SemVerRange(List.of());
        }
        if ((parts.length != 2 && parts.length != 3) || !NUMBER.matcher(parts[0]).matches()) {
            throw invalid(value);
        }
        int major = number(parts[0], value);
        if (isWildcard(parts[1])) {
            if (parts.length == 3 && !isWildcard(parts[2])) {
                throw invalid(value);
            }
            return bounded(new SemVer(major, 0, 0), new SemVer(increment(major), 0, 0));
        }
        if (parts.length != 3 || !NUMBER.matcher(parts[1]).matches() || !isWildcard(parts[2])) {
            throw invalid(value);
        }
        int minor = number(parts[1], value);
        return bounded(new SemVer(major, minor, 0), new SemVer(major, increment(minor), 0));
    }

    private static SemVer version(Matcher matcher, int offset) {
        return new SemVer(number(matcher.group(offset), matcher.group()), number(matcher.group(offset + 1), matcher.group()),
                number(matcher.group(offset + 2), matcher.group()));
    }

    private static int number(String value, String range) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(range);
        }
    }

    private static int increment(int value) {
        if (value == Integer.MAX_VALUE) {
            throw invalid(Integer.toString(value));
        }
        return value + 1;
    }

    private static boolean isWildcard(String value) {
        return value.equalsIgnoreCase("x");
    }

    private static SemVerRange bounded(SemVer lowerBound, SemVer upperBound) {
        if (lowerBound.compareTo(upperBound) >= 0) {
            throw invalid(lowerBound + " < " + upperBound);
        }
        return new SemVerRange(List.of(new Constraint(Operator.GREATER_THAN_OR_EQUAL, lowerBound),
                new Constraint(Operator.LESS_THAN, upperBound)));
    }

    private static boolean containsWildcard(String value) {
        return value.indexOf('x') >= 0 || value.indexOf('X') >= 0;
    }

    private static IllegalArgumentException invalid(String value) {
        return new IllegalArgumentException("Invalid semantic version range: " + value);
    }

    private record Constraint(Operator operator, SemVer version) {
        private boolean matches(SemVer candidate) {
            int comparison = candidate.compareTo(version);
            return switch (operator) {
                case EQUAL -> comparison == 0;
                case GREATER_THAN_OR_EQUAL -> comparison >= 0;
                case LESS_THAN -> comparison < 0;
            };
        }
    }

    private enum Operator {
        EQUAL, GREATER_THAN_OR_EQUAL, LESS_THAN
    }
}
