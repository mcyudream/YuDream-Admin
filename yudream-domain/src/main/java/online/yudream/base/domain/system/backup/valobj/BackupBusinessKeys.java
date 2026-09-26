package online.yudream.base.domain.system.backup.valobj;

import java.util.Map;

/**
 * 备份合并导入的业务键映射：这些集合除 _id 外存在逻辑唯一键
 * （种子/初始化器会在新库重建同业务键、不同 _id 的记录），
 * 判重与覆盖必须按业务键进行，否则合并导入会产生同键双文档。
 */
public final class BackupBusinessKeys {

    private static final Map<String, String> FIELDS = Map.of(
            "sysPermission", "code",
            "sysMenu", "code",
            "sysRole", "code",
            "sysUser", "username",
            "sysSetting", "key");

    private BackupBusinessKeys() {
    }

    /** 返回集合的业务键字段名；无业务键的集合返回 null。 */
    public static String fieldOf(String collection) {
        return FIELDS.get(collection);
    }
}
