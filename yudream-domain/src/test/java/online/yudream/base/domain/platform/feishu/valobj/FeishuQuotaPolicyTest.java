package online.yudream.base.domain.platform.feishu.valobj;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 飞书调用额度策略：默认值回落、显式配置生效、非法值拒绝。
 */
class FeishuQuotaPolicyTest {

    @Test
    void fallsBackToDefaultsWhenConfigMissingOrInvalid() {
        assertEquals(FeishuQuotaPolicy.DEFAULT_PER_MINUTE_LIMIT, FeishuQuotaPolicy.fromConfig(null).perMinuteLimit());
        assertEquals(FeishuQuotaPolicy.DEFAULT_DAILY_LIMIT, FeishuQuotaPolicy.fromConfig(Map.of()).dailyLimit());

        Map<String, String> broken = new HashMap<>();
        broken.put(FeishuQuotaPolicy.PER_MINUTE_CONFIG_KEY, "abc");
        broken.put(FeishuQuotaPolicy.DAILY_CONFIG_KEY, "-5");
        FeishuQuotaPolicy policy = FeishuQuotaPolicy.fromConfig(broken);
        assertEquals(FeishuQuotaPolicy.DEFAULT_PER_MINUTE_LIMIT, policy.perMinuteLimit());
        assertEquals(FeishuQuotaPolicy.DEFAULT_DAILY_LIMIT, policy.dailyLimit());
    }

    @Test
    void defaultsAreTwentyPerMinuteAndFiveHundredPerDay() {
        assertEquals(20, FeishuQuotaPolicy.DEFAULT_PER_MINUTE_LIMIT);
        assertEquals(500, FeishuQuotaPolicy.DEFAULT_DAILY_LIMIT);
        FeishuQuotaPolicy policy = FeishuQuotaPolicy.of(null, null);
        assertEquals(20, policy.perMinuteLimit());
        assertEquals(500, policy.dailyLimit());
    }

    @Test
    void explicitConfigWins() {
        Map<String, String> config = Map.of(
                FeishuQuotaPolicy.PER_MINUTE_CONFIG_KEY, " 60 ",
                FeishuQuotaPolicy.DAILY_CONFIG_KEY, "1000");
        FeishuQuotaPolicy policy = FeishuQuotaPolicy.fromConfig(config);
        assertEquals(60, policy.perMinuteLimit());
        assertEquals(1000, policy.dailyLimit());
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThrows(BizException.class, () -> new FeishuQuotaPolicy(0, 100));
        assertThrows(BizException.class, () -> new FeishuQuotaPolicy(10, 0));
        assertThrows(BizException.class, () -> new FeishuQuotaPolicy(-1, -1));
    }
}
