package online.yudream.base.infra.system.log.impl;

import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.domain.system.log.model.SystemLogQuery;
import online.yudream.base.domain.system.log.repo.SystemLogRepo;
import online.yudream.base.infra.system.log.service.LogModuleResolver;
import online.yudream.base.infra.system.log.service.SystemLogBuffer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class SystemLogRepoImpl implements SystemLogRepo {

    private final LogModuleResolver moduleResolver = new LogModuleResolver();

    @Override
    public List<SystemLogEntry> recent(SystemLogQuery query) {
        return SystemLogBuffer.instance().recent(query);
    }

    @Override
    public List<String> modules() {
        Set<String> modules = new LinkedHashSet<>(moduleResolver.knownModules());
        modules.addAll(SystemLogBuffer.instance().observedModules());
        return new ArrayList<>(modules);
    }

    @Override
    public AutoCloseable subscribe(SystemLogQuery query, Consumer<SystemLogEntry> consumer) {
        return SystemLogBuffer.instance().subscribe(query, consumer);
    }

    @Override
    public void clear() {
        SystemLogBuffer.instance().clear();
    }

    @Override
    public long size() {
        return SystemLogBuffer.instance().size();
    }

    @Override
    public long droppedCount() {
        return SystemLogBuffer.instance().droppedCount();
    }

    @Override
    public int maxEntries() {
        return SystemLogBuffer.instance().maxEntries();
    }
}
