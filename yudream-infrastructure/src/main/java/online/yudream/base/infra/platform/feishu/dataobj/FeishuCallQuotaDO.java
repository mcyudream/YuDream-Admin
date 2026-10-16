package online.yudream.base.infra.platform.feishu.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.infra.common.baseobj.BaseDO;

/**
 * 飞书调用额度计数桶：按 (用户, 作用域, 窗口类型, 窗口键) 唯一。
 * <p>
 * 分钟窗口与日窗口各占一行，计数只经条件自增（CAS）变更，供多实例并发安全地共享同一额度。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FeishuCallQuotaDO extends BaseDO {

    private Long userId;
    private String scope;
    /** MINUTE / DAY */
    private String windowType;
    /** 分钟窗口 yyyyMMddHHmm，日窗口 yyyyMMdd（上海时区） */
    private String windowKey;
    private long count;
    private long limit;
}
