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
 * 插件市场源应用服务：能力双闸门、源管理与目录快照同步。多源路径激活时市场读取快照，
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

    // ---------- 源管理 ----------

    @Transactional(readOnly = true)
    public List<PluginMarketSourceDTO> list() {
        ensureEnabled();
        Map<Long, Integer> counts = pluginMarketSourceSnapshotRepo.findAll().stream()
                .collect(Collectors.toMap(PluginMarketSourceSnapshot::sourceId,
                        snapshot -> snapshot.entries().size(), (a, b) -> a));
        return pluginMarketSourceRepo.findAll().stream()
                .map(source -> PluginMarketSourceAssembler.toDTO(source, counts.get(source.getId())))
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
        validateRootUrl(cmd.getRootUrl());
        PluginMarketSource source = PluginMarketSource.builder()
                .code(code)
                .name(cmd.getName().trim())
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
            validateRootUrl(cmd.getRootUrl());
            source.setRootUrl(cmd.getRootUrl().trim());
        }
        if (StringUtils.hasText(cmd.getToken())) {
            source.setToken(cmd.getToken().trim());
        }
        if (cmd.getSortOrder() != null) {
            source.setSortOrder(cmd.getSortOrder());
        }
        return PluginMarketSourceAssembler.toDTO(pluginMarketSourceRepo.save(source), pluginCount(source.getId()));
    }

    @Transactional
    public void delete(Long id) {
        ensureEnabled();
        PluginMarketSource source = requireById(id);
        if (source.builtIn()) {
            throw new BizException("内置市场源不可删除，可在配置中调整其地址");
        }
        pluginMarketSourceRepo.deleteById(id);
        pluginMarketSourceSnapshotRepo.deleteBySourceId(id);
    }

    @Transactional
    public PluginMarketSourceDTO enable(Long id) {
        ensureEnabled();
        PluginMarketSource source = requireById(id);
        source.enable();
        return PluginMarketSourceAssembler.toDTO(pluginMarketSourceRepo.save(source), pluginCount(id));
    }

    @Transactional
    public PluginMarketSourceDTO disable(Long id) {
        ensureEnabled();
        PluginMarketSource source = requireById(id);
        source.disable();
        return PluginMarketSourceAssembler.toDTO(pluginMarketSourceRepo.save(source), pluginCount(id));
    }

    // ---------- 同步与测试 ----------

    @Transactional
    public PluginMarketSourceDTO sync(Long id) {
        ensureEnabled();
        PluginMarketSource source = requireById(id);
        sync(source);
        return PluginMarketSourceAssembler.toDTO(source, pluginCount(id));
    }

    @Transactional
    public List<PluginMarketSourceDTO> syncAll() {
        ensureEnabled();
        for (PluginMarketSource source : pluginMarketSourceRepo.findAll()) {
            if (!source.enabled()) {
                continue;
            }
            try {
                sync(source);
            } catch (RuntimeException e) {
                // 单源失败不拖垮整体同步，错误已记录在源的同步状态中。
            }
        }
        return list();
    }

    /** 测试尚未保存的源表单：做一次只读目录探测，不落任何状态。 */
    public PluginMarketSourceTestResultDTO test(PluginMarketSourceTestCmd cmd) {
        ensureEnabled();
        validateRootUrl(cmd.getRootUrl());
        try {
            List<PluginStoreCatalogEntry> entries = pluginStoreGateway.fetchCatalog(
                    new PluginStoreSourceRef(cmd.getRootUrl().trim(),
                            StringUtils.hasText(cmd.getToken()) ? cmd.getToken().trim() : null));
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

    /** 多源路径是否需要项目闸门 + 应用闸门同时放行，见 isActive。 */
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

    private PluginMarketSource requireById(Long id) {
        return pluginMarketSourceRepo.findById(id)
                .orElseThrow(() -> new BizException("插件市场源不存在"));
    }

    private Integer pluginCount(Long sourceId) {
        return pluginMarketSourceSnapshotRepo.findBySourceId(sourceId)
                .map(snapshot -> snapshot.entries().size())
                .orElse(0);
    }

    private void validateRootUrl(String rootUrl) {
        if (!StringUtils.hasText(rootUrl)) {
            throw new BizException("市场源地址不能为空");
        }
        try {
            URI uri = new URI(rootUrl.trim());
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())
                    || !StringUtils.hasText(uri.getHost()) || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new BizException("市场源地址必须是 HTTPS 根地址（index.json 完整地址）");
            }
        } catch (URISyntaxException e) {
            throw new BizException("市场源地址格式不正确");
        }
    }

    public record SourceCatalog(PluginMarketSource source, PluginMarketSourceSnapshot snapshot) {
    }
}
