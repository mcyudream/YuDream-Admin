package online.yudream.base.application.system.log.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.log.dto.SystemLogStats;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.domain.system.log.model.SystemLogQuery;
import online.yudream.base.domain.system.log.repo.SystemLogRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SystemLogAppService {

    /** 单次扫描上限，远超内存缓冲默认容量，保证 page/export 能覆盖全部匹配日志。 */
    private static final int SCAN_LIMIT = 100_000;

    private final SystemLogRepo systemLogRepo;

    public PageResult<SystemLogEntry> page(String level, Set<String> modules, String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.clamp(size, 1, 200);
        List<SystemLogEntry> all = systemLogRepo.recent(SystemLogQuery.of(level, modules, keyword, SCAN_LIMIT));
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), safePage, safeSize);
    }

    public List<SystemLogEntry> export(String level, Set<String> modules, String keyword) {
        return systemLogRepo.recent(SystemLogQuery.of(level, modules, keyword, SCAN_LIMIT));
    }

    public List<String> modules() {
        return systemLogRepo.modules();
    }

    public SystemLogStats stats() {
        return new SystemLogStats(systemLogRepo.size(), systemLogRepo.droppedCount(), systemLogRepo.maxEntries());
    }

    public AutoCloseable subscribe(String level, Set<String> modules, String keyword, Consumer<SystemLogEntry> consumer) {
        return systemLogRepo.subscribe(SystemLogQuery.of(level, modules, keyword, SCAN_LIMIT), consumer);
    }

    public long clear() {
        long cleared = systemLogRepo.size();
        systemLogRepo.clear();
        return cleared;
    }
}
