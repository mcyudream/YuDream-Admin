package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchivePathsTest {

    @Test
    void objectKeyEncodingRoundTrip() {
        String key = "module/2026/09/26/中文 文件 name+1.png";
        String encoded = ArchivePaths.encodeObjectKey(key);
        assertEquals(key, ArchivePaths.decodeObjectKey(encoded));
    }

    @Test
    void rejectsUnsafeObjectKeys() {
        assertThrows(BizException.class, () -> ArchivePaths.validateObjectKey(null));
        assertThrows(BizException.class, () -> ArchivePaths.validateObjectKey("/abs"));
        assertThrows(BizException.class, () -> ArchivePaths.validateObjectKey("a/../b"));
        assertThrows(BizException.class, () -> ArchivePaths.validateObjectKey("a\\b"));
        assertThrows(BizException.class, () -> ArchivePaths.validateObjectKey("dir//x"));
    }

    @Test
    void rejectsUnsafeRelativePaths() {
        assertThrows(BizException.class, () -> ArchivePaths.validateRelative("../escape"));
        assertThrows(BizException.class, () -> ArchivePaths.validateRelative("/abs"));
        assertThrows(BizException.class, () -> ArchivePaths.validateRelative("a/b c.txt"));
        ArchivePaths.validateRelative("world/level.dat");
        ArchivePaths.validateRelative("servers/a1/config.properties");
    }
}
