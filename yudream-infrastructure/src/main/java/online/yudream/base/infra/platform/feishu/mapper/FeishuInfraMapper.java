package online.yudream.base.infra.platform.feishu.mapper;

import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaSnapshot;
import online.yudream.base.infra.platform.feishu.dataobj.FeishuCallQuotaDO;

/**
 * 飞书额度 domain <-> dataobj 转换。
 */
public final class FeishuInfraMapper {

    private FeishuInfraMapper() {
    }

    public static FeishuQuotaSnapshot quotaSnapshot(Long userId, FeishuQuotaScope scope,
                                                    FeishuCallQuotaDO minute, FeishuCallQuotaDO day) {
        return new FeishuQuotaSnapshot(
                userId,
                scope,
                minute == null ? null : minute.getWindowKey(),
                minute == null ? 0L : minute.getCount(),
                minute == null ? 0L : minute.getLimit(),
                day == null ? null : day.getWindowKey(),
                day == null ? 0L : day.getCount(),
                day == null ? 0L : day.getLimit());
    }
}
