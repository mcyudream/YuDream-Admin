package online.yudream.base.domain.platform.ai.repo;

import online.yudream.base.domain.platform.ai.aggregate.AiToolSettings;

public interface AiToolSettingsRepo {

    /** 读取系统工具开关设置；未初始化时返回默认值（全开）。 */
    AiToolSettings load();

    AiToolSettings save(AiToolSettings settings);
}
