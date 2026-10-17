package online.yudream.base.domain.system.log.repo;

import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.domain.system.log.model.SystemLogModuleGroup;
import online.yudream.base.domain.system.log.model.SystemLogQuery;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 系统运行日志仓储：内存环形缓冲的查询、实时订阅与清理。
 */
public interface SystemLogRepo {

    List<SystemLogEntry> recent(SystemLogQuery query);

    /** 缓存内各级别条数（键为级别名，含计数为 0 的级别），供页面按级别筛选与分布展示。 */
    Map<String, Long> levelCounts();

    List<String> modules();

    List<SystemLogModuleGroup> moduleGroups();

    AutoCloseable subscribe(SystemLogQuery query, Consumer<SystemLogEntry> consumer);

    void clear();

    long size();

    long droppedCount();

    int maxEntries();
}
