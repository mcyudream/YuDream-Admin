package online.yudream.base.application.platform.plugin.query;

import lombok.Data;

/**
 * 插件目录分页查询：筛选、排序与分页全部在服务端完成，前端只提交条件与页码。
 * 未知的 status / updateState / sort 取值按默认语义处理，避免脏参数打回 400。
 */
@Data
public class PluginModulePageQuery {

    private int page = 1;

    private int size = 10;

    /** 关键词：匹配名称、编码、版本与描述。 */
    private String keyword;

    /** 运行状态：ALL（默认）/ ENABLED / LOADED / INSTALLED / DISABLED / ERROR。 */
    private String status;

    /** 更新状态：ALL（默认）/ AVAILABLE（可更新）/ BLOCKED（更新受阻）。 */
    private String updateState;

    /** 排序：DEFAULT（按编码）/ NAME（名称）/ STATUS（异常优先）/ RECENT（最近加载）。 */
    private String sort;
}
