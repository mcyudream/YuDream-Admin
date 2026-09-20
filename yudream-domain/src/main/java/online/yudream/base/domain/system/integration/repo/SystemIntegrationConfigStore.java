package online.yudream.base.domain.system.integration.repo;

import online.yudream.base.domain.system.integration.enumerate.IntegrationCategory;

import java.util.Map;

/**
 * 系统集成配置存取端口（持久化到 sys_setting，secret 字段由实现侧加密）。
 * 值语义：map 中只包含非空值；保存空串等价于清除该配置项（回落环境变量）。
 */
public interface SystemIntegrationConfigStore {

    /**
     * 读取分类下已保存的非空值；secret 字段返回解密后的明文。
     */
    Map<String, String> load(IntegrationCategory category);

    /**
     * 逐项保存（upsert）；传入空串表示清除该项。
     */
    void save(IntegrationCategory category, Map<String, String> values);
}
