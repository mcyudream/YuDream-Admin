package online.yudream.base.domain.system.log.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemLogQueryTest {

    private SystemLogEntry entry(String logger, String message) {
        return new SystemLogEntry(1, System.currentTimeMillis(), SystemLogLevel.INFO,
                logger, "系统", "main", null, message, null);
    }

    @Test
    void matchesLoggerPrefix() {
        SystemLogQuery query = new SystemLogQuery(null, Set.of(), null, 100, "online.yudream.base.plugin.wordle");
        assertTrue(query.matches(entry("online.yudream.base.plugin.wordle.bootstrap.WordlePlugin", "loaded")));
        assertTrue(query.matches(entry("online.yudream.base.plugin.wordle2.GameService", "x")));
        assertFalse(query.matches(entry("online.yudream.base.application.SomeService", "x")));
        assertFalse(query.matches(entry("online.yudream.base.plugin", "x")));
    }

    @Test
    void blankLoggerPrefixIsIgnored() {
        SystemLogQuery query = new SystemLogQuery(null, Set.of(), null, 100, "  ");
        assertNull(query.loggerPrefix());
        assertTrue(query.matches(entry("any.Logger", "x")));
    }

    @Test
    void legacyFourArgConstructorKeepsNullPrefix() {
        SystemLogQuery query = SystemLogQuery.of("info", Set.of(), null, 100);
        assertNull(query.loggerPrefix());
        assertTrue(query.matches(entry("online.yudream.base.plugin.wordle.X", "x")));
    }
}
