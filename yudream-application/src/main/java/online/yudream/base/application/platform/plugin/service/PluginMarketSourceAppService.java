package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.assembler.PluginMarketSourceAssembler;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketSourceCreateCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketSourceTestCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketSourceUpdateCmd;
import online.yudream.base.application.platform.plugin.dto.PluginMarketSourceDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketSourceTestResultDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceType;
import online.yudream.base.domain.platform.plugin.port.PluginStoreGateway;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceRepo;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceSnapshotRepo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMarketSourceSnapshot;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreSourceRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 插件市场源应用服务：能力双闸门、源管理与目录快照同步。
 * 能力未启用时市场目录为空、无 Nexus 隐式回落；LOCAL 源进程内直读发布物，不建快照。
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
    private final PluginMarketPublicationAppService pluginMarketPublicationAppService;

    @Value("${yudream.platform.capabilities.plugin-market-source.enabled:true}")
    private boolean projectGateEnabled;

    /** 多源路径是否生效：项目闸门开启且能力已在平台能力中启用。 */
    public boolean isActive() {
        return projectGateEnabled && capabilityAppService.enabled(CAPABILITY_CODE);
    }

    public void ensureEnabled() {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, CAPABILITY_NAME);
    }

    // ---------- 源管理 ----------

    @Transactional(readOnly = true)
    public List<PluginMarketSourceDTO> list() {
        ensureEnabled();
        Map<Long, Integer> counts = pluginMarketSourceSnapshotRepo.findAll().stream()
                .collect(Collectors.toMap(PluginMarketSourceSnapshot::sourceId,
                        snapshot -> snapshot.entries().size(), (a, b) -> a));
        return pluginMarketSourceRepo.findAll().stream()
                .map(source -> PluginMarketSourceAssembler.toDTO(source, pluginCount(source, counts)))
                .toList();
    }

    @Transactional
    public PluginMarketSourceDTO create(PluginMarketSourceCreateCmd cmd) {
        ensureEnabled();
        String code = cmd.getCode() == null ? "" : cmd.getCode().trim();
        if (!code.matches("[a-z0-9][a-z0-9-]{0,31}")) {
            throw new BizException("市场源标识只能是 32 位以内的小写字母、数字或连字符");
        }
        if (pluginMarketSourceRepo.findByCode(code).isPresent()) {
            throw new BizException("市场源标识已存在：" + code);
        }
        if (!StringUtils.hasText(cmd.getName())) {
            throw new BizException("市场源名称不能为空");
        }
        MarketSourceType type = parseRemoteType(cmd.getType());
        validateRootUrl(cmd.getRootUrl(), type);
        PluginMarketSource source = PluginMarketSource.builder()
                .code(code)
                .name(cmd.getName().trim())
                .type(type)
                .rootUrl(cmd.getRootUrl().trim())
                .token(StringUtils.hasText(cmd.getToken()) ? cmd.getToken().trim() : null)
                .enabled(true)
                .builtIn(false)
                .sortOrder(cmd.getSortOrder() == null ? 100 : cmd.getSortOrder())
                .build();
        return PluginMarketSourceAssembler.toDTO(pluginMarketSourceRepo.save(source), 0);
    }

    @Transactional
    public PluginMarketSourceDTO update(PluginMarketSourceUpdateCmd cmd) {
        ensureEnabled();
        PluginMarketSource source = requireById(cmd.getId());
        if (!StringUtils.hasText(cmd.getName())) {
            throw new BizException("市场源名称不能为空");
        }
        source.setName(cmd.getName().trim());
        if (!source.builtIn()) {
            MarketSourceType type = StringUtils.hasText(cmd.getType())
                    ? parseRemoteType(cmd.getType())
                    : source.type();
            if (type == MarketSourceType.LOCAL) {
                throw new BizException("本机源类型仅限内置源");
            }
            validateRootUrl(cmd.getRootUrl(), type);
            source.setType(type);
            source.setRootUrl(cmd.getRootUrl().trim());
        }
        if (StringUtils.hasText(cmd.getToken())) {
            source.setToken(cmd.getToken().trim());
        }
        if (cmd.getSortOrder() != null) {
            source.setSortOrder(cmd.getSortOrder());
        }
        return PluginMarketSourceAssembler.toDTO(pluginMarketSourceRepo.save(source), pluginCount(source));
    }

    @Transactional
    public void delete(Long id) {
        ensureEnabled();
        PluginMarketSource source = requireById(id);
        if (source.builtIn()) {
            throw new BizException("内置市场源不可删除");
        }
        pluginMarketSourceRepo.deleteById(id);
        pluginMarketSourceSnapshotRepo.deleteBySourceId(id);
    }

    @Transactional
    public PluginMarketSourceDTO enable(Long id) {
        ensureEnabled();
        PluginMarketSource source = requireById(id);
        source.enable();
        return PluginMarketSourceAssembler.toDTO(pluginMarketSourceRepo.save(source), pluginCount(source));
    }

    @Transactional
    public PluginMarketSourceDTO disable(Long id) {
        ensureEnabled();
        PluginMarketSource source = requireById(id);
        source.disable();
        return PluginMarketSourceAssembler.toDTO(pluginMarketSourceRepo.save(source), pluginCount(source));
    }

    // ---------- 同步与测试 ----------

    @Transactional
    public PluginMarketSourceDTO sync(Long id) {
        ensureEnabled();
        PluginMarketSource source = requireById(id);
        if (source.type() == MarketSourceType.LOCAL) {
            source.markSynced();
            pluginMarketSourceRepo.save(source);
            return PluginMarketSourceAssembler.toDTO(source, pluginCount(source));
        }
        sync(source);
        return PluginMarketSourceAssembler.toDTO(source, pluginCount(source));
    }

    @Transactional
    public List<PluginMarketSourceDTO> syncAll() {
        ensureEnabled();
        for (PluginMarketSource source : pluginMarketSourceRepo.findAll()) {
            if (!source.enabled()) {
                continue;
            }
            try {
                if (source.type() == MarketSourceType.LOCAL) {
                    source.markSynced();
                    pluginMarketSourceRepo.save(source);
                } else {
                    sync(source);
                }
            } catch (RuntimeException e) {
                // 单源失败不拖垮整体同步，错误已记录在源的同步状态中。
            }
        }
        return list();
    }

    /** 测试尚未保存的源表单：做一次只读目录探测，不落任何状态。LOCAL 源无需探测。 */
    public PluginMarketSourceTestResultDTO test(PluginMarketSourceTestCmd cmd) {
        ensureEnabled();
        MarketSourceType type = parseRemoteType(cmd.getType());
        validateRootUrl(cmd.getRootUrl(), type);
        try {
            List<PluginStoreCatalogEntry> entries = pluginStoreGateway.fetchCatalog(
                    new PluginStoreSourceRef(cmd.getRootUrl().trim(),
                            StringUtils.hasText(cmd.getToken()) ? cmd.getToken().trim() : null,
                            type));
            return PluginMarketSourceTestResultDTO.builder()
                    .ok(true)
                    .pluginCount(entries.size())
                    .message("连接成功，发现 " + entries.size() + " 个插件")
                    .build();
        } catch (BizException e) {
            return PluginMarketSourceTestResultDTO.builder()
                    .ok(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    /** 同步串行化：目录拉取是长外呼，避免并发重复同步。 */
    public synchronized PluginMarketSourceSnapshot sync(PluginMarketSource source) {
        if (source.type() == MarketSourceType.LOCAL) {
            throw new BizException("本机源无需同步");
        }
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

    // ---------- 目录视图支撑（PluginStoreAppService 消费） ----------

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
        return new PluginStoreSourceRef(source.getRootUrl(), source.getToken(), source.type());
    }

    public java.nio.file.Path resolveLocalJar(String localUrl) {
        return pluginMarketPublicationAppService.resolveLocalJar(localUrl);
    }

    /**
     * 启用源及其目录：LOCAL 源进程内直读发布物（不建快照）；远端源读快照，缺失时内联同步，
     * 同步失败的源跳过（错误已记录在源同步状态）。
     */
    public List<SourceCatalog> enabledSourceCatalogs() {
        List<SourceCatalog> result = new ArrayList<>();
        for (PluginMarketSource source : enabledSources()) {
            if (source.type() == MarketSourceType.LOCAL) {
                result.add(new SourceCatalog(source, new PluginMarketSourceSnapshot(
                        source.getId(), LocalDateTime.now(), pluginMarketPublicationAppService.localCatalogEntries())));
                continue;
            }
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

    private PluginMarketSource requireById(Long id) {
        return pluginMarketSourceRepo.findById(id)
                .orElseThrow(() -> new BizException("插件市场源不存在"));
    }

    private Integer pluginCount(PluginMarketSource source) {
        if (source.type() == MarketSourceType.LOCAL) {
            return pluginMarketPublicationAppService.localCatalogEntries().size();
        }
        return pluginMarketSourceSnapshotRepo.findBySourceId(source.getId())
                .map(snapshot -> snapshot.entries().size())
                .orElse(0);
    }

    private Integer pluginCount(PluginMarketSource source, Map<Long, Integer> snapshotCounts) {
        if (source.type() == MarketSourceType.LOCAL) {
            return pluginMarketPublicationAppService.localCatalogEntries().size();
        }
        return snapshotCounts.getOrDefault(source.getId(), 0);
    }

    private MarketSourceType parseRemoteType(String type) {
        if (!StringUtils.hasText(type)) {
            return MarketSourceType.STATIC_INDEX;
        }
        try {
            MarketSourceType parsed = MarketSourceType.valueOf(type.trim());
            if (parsed == MarketSourceType.LOCAL) {
                throw new BizException("本机源类型仅限内置源，创建时请选择静态索引或 v2 协议");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new BizException("未知的市场源类型：" + type);
        }
    }

    private void validateRootUrl(String rootUrl, MarketSourceType type) {
        if (type == MarketSourceType.LOCAL) {
            return;
        }
        if (!StringUtils.hasText(rootUrl)) {
            throw new BizException("市场源地址不能为空");
        }
        try {
            URI uri = new URI(rootUrl.trim());
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())
                    || !StringUtils.hasText(uri.getHost()) || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new BizException(type == MarketSourceType.V2_API
                        ? "市场源地址必须是 HTTPS 根地址（如 https://host/api/public/plugin-market）"
                        : "市场源地址必须是 HTTPS 根地址（index.json 完整地址）");
            }
        } catch (URISyntaxException e) {
            throw new BizException("市场源地址格式不正确");
        }
    }

    public record SourceCatalog(PluginMarketSource source, PluginMarketSourceSnapshot snapshot) {
    }
}
