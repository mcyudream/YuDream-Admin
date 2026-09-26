package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.dto.PluginBackupTargetCatalogDTO;
import online.yudream.base.application.system.backup.service.RemoteBackupAppService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 插件前端备份目标目录：暴露启用中的异地备份目标（编码/名称/类型），
 * 供插件界面渲染统一的目标选择器（如实例计划任务、手动异地备份），
 * 不再要求管理员手抄目标编码。不含 host/凭据等敏感字段。
 */
@Service
@RequiredArgsConstructor
public class PluginBackupCatalogAppService {

    private final RemoteBackupAppService remoteBackupAppService;

    public List<PluginBackupTargetCatalogDTO> targets() {
        return remoteBackupAppService.targets().stream()
                .filter(target -> target.enabled())
                .map(target -> new PluginBackupTargetCatalogDTO(
                        target.code(), target.name(), target.type()))
                .toList();
    }
}
