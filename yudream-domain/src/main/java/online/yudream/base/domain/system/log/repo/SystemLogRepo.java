package online.yudream.base.domain.system.log.repo;

import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.domain.system.log.model.SystemLogQuery;

import java.util.List;
import java.util.function.Consumer;

/**
 * 系统运行日志仓储：内存环形缓冲的查询、实时订阅与清理。
 */
public interface SystemLogRepo {

    List<SystemLogEntry> recent(SystemLogQuery query);

    List<String> modules();

    AutoCloseable subscribe(SystemLogQuery query, Consumer<SystemLogEntry> consumer);

    void clear();

    long size();

    long droppedCount();

    int maxEntries();
}
