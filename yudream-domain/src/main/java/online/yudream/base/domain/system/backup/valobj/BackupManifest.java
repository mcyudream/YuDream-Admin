package online.yudream.base.domain.system.backup.valobj;

import java.util.List;

/**
 * YDBA（YuDream Backup Archive）归档清单。
 * 归档为 ZIP：{@code mongo/{collection}.ndjson}（EJSON EXTENDED 逐行文档）、
 * {@code objects/{编码后对象键}} 与 {@code objects.index.json}、
 * {@code plugins/{pluginCode}/{scopeCode}/{path}} 与 {@code plugins.index.json}、{@code manifest.json}。
 */
public record BackupManifest(
        String format,
        int schemaVersion,
        String createdAt,
        String hostVersion,
        String masterKeyFingerprint,
        List<ManifestScope> scopes,
        List<ManifestCollection> collections,
        long objectCount,
        long objectBytes,
        List<ManifestPluginScope> pluginScopes
) {

    public static final String FORMAT = "yudream-backup";
    public static final int SCHEMA_VERSION = 1;

    public record ManifestScope(String tag, String displayName) {
    }

    public record ManifestCollection(String name, long count, String sha256) {
    }

    public record ManifestPluginScope(String pluginCode, String scopeCode, int fileCount, long bytes) {
    }

    /** 校验归档声明格式与协议版本，不匹配抛出业务异常。 */
    public void ensureSupported() {
        if (!FORMAT.equals(format)) {
            throw new online.yudream.base.domain.common.exception.BizException("不是有效的系统备份归档（format=" + format + "）");
        }
        if (schemaVersion > SCHEMA_VERSION) {
            throw new online.yudream.base.domain.common.exception.BizException(
                    "备份归档协议版本过新（v" + schemaVersion + " > v" + SCHEMA_VERSION + "），请先升级宿主");
        }
    }
}
