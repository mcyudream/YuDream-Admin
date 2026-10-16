package online.yudream.base.domain.platform.feishu.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.Map;

/**
 * 飞书调用额度策略：每用户每分钟与每日的云文档 API 请求上限。
 * <p>
 * 上限来自能力配置；新增配置键必须在读取处运行时回落默认值，因此默认常量定义在这里。
 * 默认值刻意保守：飞书侧单应用文档接口限频为读 5 次/秒、编辑 3 次/秒，单篇文档并发编辑 3 次/秒。
 */
public record FeishuQuotaPolicy(int perMinuteLimit, int dailyLimit) {

    public static final String PER_MINUTE_CONFIG_KEY = "defaultPerMinuteLimit";
    public static final String DAILY_CONFIG_KEY = "defaultDailyLimit";

    public static final int DEFAULT_PER_MINUTE_LIMIT = 20;
    public static final int DEFAULT_DAILY_LIMIT = 500;

    public FeishuQuotaPolicy {
        if (perMinuteLimit <= 0) {
            throw new BizException("飞书每分钟调用上限必须大于 0");
        }
        if (dailyLimit <= 0) {
            throw new BizException("飞书每日调用上限必须大于 0");
        }
    }

    /** 归一化外部输入：空值与非法值回落默认，保证读取处永远得到可用策略。 */
    public static FeishuQuotaPolicy of(Integer perMinuteLimit, Integer dailyLimit) {
        return new FeishuQuotaPolicy(
                perMinuteLimit == null || perMinuteLimit <= 0 ? DEFAULT_PER_MINUTE_LIMIT : perMinuteLimit,
                dailyLimit == null || dailyLimit <= 0 ? DEFAULT_DAILY_LIMIT : dailyLimit);
    }

    public static FeishuQuotaPolicy fromConfig(Map<String, String> config) {
        Map<String, String> values = config == null ? Map.of() : config;
        return of(positive(values.get(PER_MINUTE_CONFIG_KEY)), positive(values.get(DAILY_CONFIG_KEY)));
    }

    private static Integer positive(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : null;
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }
}
