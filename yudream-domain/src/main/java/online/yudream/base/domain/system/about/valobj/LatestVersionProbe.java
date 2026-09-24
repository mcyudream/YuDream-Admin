package online.yudream.base.domain.system.about.valobj;

import java.time.Instant;

/**
 * 单个构件的最新版本探测结果；探测失败时 latest 为空、error 带中文原因，不抛异常。
 */
public record LatestVersionProbe(
        String key,
        String name,
        LatestVersionTarget.Kind kind,
        String latest,
        String sourceUrl,
        Instant checkedAt,
        String error
) {

    public boolean success() {
        return latest != null && !latest.isBlank() && error == null;
    }
}
