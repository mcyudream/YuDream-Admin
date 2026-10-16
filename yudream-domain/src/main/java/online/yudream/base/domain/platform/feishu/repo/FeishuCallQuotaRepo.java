package online.yudream.base.domain.platform.feishu.repo;

import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaPolicy;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaSnapshot;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaWindowKeys;

import java.time.Instant;
import java.util.Optional;

/**
 * 飞书调用额度仓储：分钟窗口与日窗口的持久化计数。
 * <p>
 * 计数必须落在持久存储并用原子条件自增实现，多实例部署下不得依赖进程内计数；
 * 预占在调用飞书之前完成，退还只发生在飞书明确拒绝、未真正执行调用时。
 */
public interface FeishuCallQuotaRepo {

    /** 读取当前窗口用量（不存在时建立零计数桶并按策略刷新上限）。 */
    FeishuQuotaSnapshot snapshot(Long userId, FeishuQuotaScope scope, Instant now, FeishuQuotaPolicy policy);

    /**
     * 调用前原子预占一次额度：分钟窗口与日窗口同时占用成功才算通过。
     *
     * @return 预占使用的窗口键；任一窗口超限返回 {@link Optional#empty()}（此时不产生任何占用）
     */
    Optional<FeishuQuotaWindowKeys> reserve(Long userId, FeishuQuotaScope scope, Instant now, FeishuQuotaPolicy policy);

    /** 退还一次预占，必须携带 {@link #reserve} 返回的窗口键。 */
    void refund(Long userId, FeishuQuotaScope scope, FeishuQuotaWindowKeys windows);
}
