package online.yudream.base.infra.system.backup.service;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 合并导入的 _id 类型变体探测：负数雪花 / 正数雪花 / ObjectId / 字符串。 */
class MongoBackupRestoreStoreIdVariantsTest {

    @Test
    void negativeSnowflakeIdGetsLongVariant() {
        List<Object> variants = MongoBackupRestoreStore.idVariants("-1740829301");
        assertTrue(variants.contains(-1740829301L));
        assertTrue(variants.contains("-1740829301"));
        assertEquals(2, variants.size());
    }

    @Test
    void positiveSnowflakeIdGetsLongVariant() {
        List<Object> variants = MongoBackupRestoreStore.idVariants("362139556629319680");
        assertTrue(variants.contains(362139556629319680L));
        assertTrue(variants.contains("362139556629319680"));
    }

    @Test
    void objectIdHexGetsObjectIdVariant() {
        String hex = "5f1f34a1d4b2c8a3e6f0b1c2";
        List<Object> variants = MongoBackupRestoreStore.idVariants(hex);
        assertTrue(variants.contains(new ObjectId(hex)));
        assertTrue(variants.contains(hex));
    }

    @Test
    void plainStringKeepsStringVariantOnly() {
        assertEquals(List.of("role-admin"), MongoBackupRestoreStore.idVariants("role-admin"));
    }

    @Test
    void leadingZerosParseNumerically() {
        List<Object> variants = MongoBackupRestoreStore.idVariants("007");
        assertTrue(variants.contains(7L));
        assertTrue(variants.contains("007"));
    }
}
