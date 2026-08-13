package online.yudream.base.domain.platform.plugin.valobj;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restricted semantic version used by the plugin marketplace contract.
 */
public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    private static final Pattern VERSION = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

    public SemVer {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Semantic version components must not be negative");
        }
    }

    public static SemVer parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Semantic version is required");
        }
        Matcher matcher = VERSION.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version: " + value);
        }
        try {
            return new SemVer(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid semantic version: " + value, exception);
        }
    }

    @Override
    public int compareTo(SemVer other) {
        int majorComparison = Integer.compare(major, other.major);
        if (majorComparison != 0) {
            return majorComparison;
        }
        int minorComparison = Integer.compare(minor, other.minor);
        return minorComparison != 0 ? minorComparison : Integer.compare(patch, other.patch);
    }
}
