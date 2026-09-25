package online.yudream.base.domain.system.backup;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.enumerate.BackupScopeType;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackupScopeRefTest {

    @Test
    void systemTagRoundTrip() {
        BackupScopeRef ref = BackupScopeRef.parse("system");
        assertEquals(BackupScopeType.SYSTEM, ref.type());
        assertEquals("system", ref.tag());
    }

    @Test
    void pluginTagRoundTrip() {
        BackupScopeRef ref = BackupScopeRef.parse("plugin:mcpanel/server-data");
        assertEquals(BackupScopeType.PLUGIN, ref.type());
        assertEquals("mcpanel", ref.pluginCode());
        assertEquals("server-data", ref.scopeCode());
        assertEquals("plugin:mcpanel/server-data", ref.tag());
    }

    @Test
    void rejectsInvalidTags() {
        assertThrows(BizException.class, () -> BackupScopeRef.parse("plugin:mcpanel"));
        assertThrows(BizException.class, () -> BackupScopeRef.parse("plugin:mcpanel/"));
        assertThrows(BizException.class, () -> BackupScopeRef.parse("bogus"));
        assertThrows(IllegalArgumentException.class, () -> BackupScopeRef.plugin("Bad_Code", "x", "x"));
        assertThrows(IllegalArgumentException.class, () -> BackupScopeRef.plugin("mcpanel", "Bad_Scope", "x"));
    }
}
