package online.yudream.base.infra.common.cache.preheat;

/**
 * 缓存预热任务接口。
 * <p>
 * 实现该接口并注册为 Spring Bean，应用启动后会自动执行预热逻辑。
 */
public interface CachePreheatTask {

    /**
     * 任务名称，用于日志与排序。
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * 执行顺序，数值越小越先执行。
     */
    default int order() {
        return 0;
    }

    /**
     * 预热逻辑，例如批量查询数据库并写入缓存。
     */
    void preheat();
}
