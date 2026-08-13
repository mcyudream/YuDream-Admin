package online.yudream.base.domain.platform.plugin.valobj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemVerRangeTest {

    @Test
    void parsesAndComparesStrictVersions() {
        assertTrue(SemVer.parse("1.2.3").compareTo(SemVer.parse("1.2.2")) > 0);
        for (String value : new String[]{"1.2", "1.2.3-beta", "01.2.3", "1.2.3+build", "2147483648.0.0"}) {
            assertThrows(IllegalArgumentException.class, () -> SemVer.parse(value), value);
        }
        assertThrows(IllegalArgumentException.class, () -> new SemVer(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SemVer(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SemVer(0, 0, -1));
    }

    @Test
    void matchesSupportedRanges() {
        assertTrue(SemVerRange.parse("1.2.3").matches(SemVer.parse("1.2.3")));
        assertTrue(SemVerRange.parse("^1.2.3").matches(SemVer.parse("1.9.0")));
        assertFalse(SemVerRange.parse("^1.2.3").matches(SemVer.parse("2.0.0")));
        assertTrue(SemVerRange.parse("^0.2.3").matches(SemVer.parse("0.2.9")));
        assertFalse(SemVerRange.parse("^0.2.3").matches(SemVer.parse("0.3.0")));
        assertTrue(SemVerRange.parse("^0.0.3").matches(SemVer.parse("0.0.3")));
        assertFalse(SemVerRange.parse("^0.0.3").matches(SemVer.parse("0.0.4")));
        assertTrue(SemVerRange.parse("~1.2.3").matches(SemVer.parse("1.2.9")));
        assertFalse(SemVerRange.parse("~1.2.3").matches(SemVer.parse("1.3.0")));
        assertTrue(SemVerRange.parse(">=1.0.0 <2.0.0").matches(SemVer.parse("1.9.9")));
        assertFalse(SemVerRange.parse(">=1.0.0 <2.0.0").matches(SemVer.parse("2.0.0")));
        assertTrue(SemVerRange.parse("1.x").matches(SemVer.parse("1.99.99")));
        assertTrue(SemVerRange.parse("1.x.x").matches(SemVer.parse("1.99.99")));
        assertTrue(SemVerRange.parse("1.2.x").matches(SemVer.parse("1.2.0")));
        assertFalse(SemVerRange.parse("1.2.x").matches(SemVer.parse("1.3.0")));
        for (String value : new String[]{"x", "X", "x.x", "X.X", "x.x.x", "X.X.X"}) {
            assertTrue(SemVerRange.parse(value).matches(SemVer.parse("0.0.0")), value);
            assertTrue(SemVerRange.parse(value).matches(SemVer.parse("2147483647.2147483647.2147483647")), value);
        }
    }

    @Test
    void rejectsUnsupportedRangesAndDerivedBoundOverflows() {
        for (String value : new String[]{"*", "1.2.3-beta", "^1.2", "~1.2", ">=1.0.0  <2.0.0", ">1.0.0", "<=1.0.0", "=1.0.0", "^1.0.0 || ^2.0.0", "[1.0,2.0)", "1.2", "1.2.*", "v1.2.3", ">=2.0.0 <1.0.0", ">=1.0.0 <1.0.0", "2147483647.x", "1.2147483647.x", "^2147483647.0.0", "^0.2147483647.0", "^0.0.2147483647", "~1.2147483647.0"}) {
            assertThrows(IllegalArgumentException.class, () -> SemVerRange.parse(value), value);
        }
    }
}
