package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.port.PluginStoreGateway;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceRepo;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceSnapshotRepo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMarketSourceSnapshot;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreSourceRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件市场源应用服务：能力双闸门、源目录快照同步。多源路径激活时市场读取快照，
 * 未激活时市场回落配置直连的单源路径，行为与历史版本一致。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PluginMarketSourceAppService {

    public static final String CAPABILITY_CODE = "plugin-market-source";
    public static final String CAPABILITY_NAME = "插件市场源";

    private final PluginStoreGateway pluginStoreGateway;
    private final PluginMarketSourceRepo pluginMarketSourceRepo;
    private final PluginMarketSourceSnapshotRepo pluginMarketSourceSnapshotRepo;
    private final CapabilityAppService capabilityAppService;

    @Value("${yudream.platform.capabilities.plugin-market-source.enabled:true}")
    private boolean projectGateEnabled;

    /** 多源路径是否生效：项目闸门开启且能力已在平台能力中启用。 */
    public boolean isActive() {
        return projectGateEnabled && capabilityAppService.enabled(CAPABILITY_CODE);
    }

    public void ensureEnabled() {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, CAPABILITY_NAME);
    }

    public List<PluginMarketSource> enabledSources() {
        return pluginMarketSourceRepo.findAll().stream()
                .filter(PluginMarketSource::enabled)
                .toList();
    }

    public PluginMarketSource requireByCode(String code) {
        return pluginMarketSourceRepo.findByCode(code)
                .orElseThrow(() -> new BizException("插件市场源不存在：" + code));
    }

    public PluginStoreSourceRef sourceRef(PluginMarketSource source) {
        return new PluginStoreSourceRef(source.getRootUrl(), source.getToken());
    }

    /** 启用源及其目录快照；快照缺失时内联同步，同步失败的源跳过（错误已记录在源同步状态）。 */
    public List<SourceCatalog> enabledSourceCatalogs() {
        List<SourceCatalog> result = new ArrayList<>();
        for (PluginMarketSource source : enabledSources()) {
            PluginMarketSourceSnapshot snapshot = pluginMarketSourceSnapshotRepo.findBySourceId(source.getId()).orElse(null);
            if (snapshot == null || snapshot.entries().isEmpty()) {
                try {
                    snapshot = sync(source);
                } catch (RuntimeException e) {
                    log.warn("插件市场源 {} 同步失败：{}", source.getCode(), e.getMessage());
                    continue;
                }
            }
            result.add(new SourceCatalog(source, snapshot));
        }
        return result;
    }

    /** 同步串行化：目录拉取是长外呼，避免并发重复同步。 */
    public synchronized PluginMarketSourceSnapshot sync(PluginMarketSource source) {
        List<PluginStoreCatalogEntry> entries;
        try {
            entries = pluginStoreGateway.fetchCatalog(sourceRef(source));
        } catch (RuntimeException e) {
            source.markSyncError(e.getMessage());
            pluginMarketSourceRepo.save(source);
            throw e;
        }
        pluginMarketSourceSnapshotRepo.save(new PluginMarketSourceSnapshot(source.getId(), LocalDateTime.now(), entries));
        source.markSynced();
        pluginMarketSourceRepo.save(source);
        return new PluginMarketSourceSnapshot(source.getId(), source.getSyncedAt(), entries);
    }

    public record SourceCatalog(PluginMarketSource source, PluginMarketSourceSnapshot snapshot) {
    }
}
