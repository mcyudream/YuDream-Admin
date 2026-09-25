package online.yudream.base.domain.system.backup.valobj;

import java.util.List;

/** 归档头信息：由执行方在导出开始时给定，统计信息由归档写入器自动汇总。 */
public record BackupManifestHeader(
        String hostVersion,
        String masterKeyFingerprint,
        List<BackupScopeRef> scopes
) {
}
