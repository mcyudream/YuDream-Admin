package online.yudream.base.domain.platform.mobile.valobj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileSemanticVersionTest {

    @Test
    void comparesCoreSegmentsLeniently() {
        assertEquals(0, MobileSemanticVersion.parseLenient("1.2.3").compareTo(MobileSemanticVersion.parseLenient("1.2.3")));
        assertEquals(0, MobileSemanticVersion.parseLenient("1.2").compareTo(MobileSemanticVersion.parseLenient("1.2.0")));
        assertTrue(MobileSemanticVersion.parseLenient("1.10.0").compareTo(MobileSemanticVersion.parseLenient("1.9.9")) > 0);
        assertTrue(MobileSemanticVersion.parseLenient("2.0.0").compareTo(MobileSemanticVersion.parseLenient("1.99.99")) > 0);
        assertTrue(MobileSemanticVersion.parseLenient("1.2.3").compareTo(MobileSemanticVersion.parseLenient("1.2.10")) < 0);
    }

    @Test
    void treatsPreReleaseLowerThanRelease() {
        assertTrue(MobileSemanticVersion.parseLenient("1.2.0-beta.1").compareTo(MobileSemanticVersion.parseLenient("1.2.0")) < 0);
        assertTrue(MobileSemanticVersion.parseLenient("1.2.0-beta.2").compareTo(MobileSemanticVersion.parseLenient("1.2.0-beta.10")) < 0);
        assertTrue(MobileSemanticVersion.parseLenient("1.2.0-rc.1").compareTo(MobileSemanticVersion.parseLenient("1.2.0-beta.3")) > 0);
        assertTrue(MobileSemanticVersion.parseLenient("1.2.1-alpha").compareTo(MobileSemanticVersion.parseLenient("1.2.0")) > 0);
    }

    @Test
    void lenientParseRejectsGarbageButKeepsNull() {
        assertNotNull(MobileSemanticVersion.parseLenient("  1.2.3  "));
        assertNull(MobileSemanticVersion.parseLenient(null));
        assertNull(MobileSemanticVersion.parseLenient(""));
        assertNull(MobileSemanticVersion.parseLenient("v1.2.3"));
        assertNull(MobileSemanticVersion.parseLenient("abc"));
        assertNull(MobileSemanticVersion.parseLenient("1.2.3-"));
    }

    @Test
    void hostVersionGateFailsOnlyWhenBothSidesParsable() {
        assertTrue(MobileSemanticVersion.satisfiesHostVersion("1.2.3", "1.2.3"));
        assertTrue(MobileSemanticVersion.satisfiesHostVersion("1.2.4", "1.2.3"));
        assertTrue(MobileSemanticVersion.satisfiesHostVersion("1.3.0", "1.2.9"));
        assertTrue(MobileSemanticVersion.satisfiesHostVersion("2.0.0-beta.1", "1.9.9"));
        // 低于下限必须拦截
        assertTrue(!MobileSemanticVersion.satisfiesHostVersion("1.2.2", "1.2.3"));
        assertTrue(!MobileSemanticVersion.satisfiesHostVersion("1.1.0", "1.2.0"));
        assertTrue(!MobileSemanticVersion.satisfiesHostVersion("1.2.3-beta", "1.2.3"));
        // 任一侧缺失/非法：无法校验即放行
        assertTrue(MobileSemanticVersion.satisfiesHostVersion(null, "1.2.3"));
        assertTrue(MobileSemanticVersion.satisfiesHostVersion("", "1.2.3"));
        assertTrue(MobileSemanticVersion.satisfiesHostVersion("garbage", "1.2.3"));
        assertTrue(MobileSemanticVersion.satisfiesHostVersion("1.2.3", null));
        assertTrue(MobileSemanticVersion.satisfiesHostVersion("1.2.3", "not-a-version"));
    }
}
