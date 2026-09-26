package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.service.PluginBackupScopeSource;
import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;
import online.yudream.base.infra.platform.plugin.service.PluginExtensionRegistry;
import online.yudream.base.plugin.spi.system.backup.BackupExportOptions;
import online.yudream.base.plugin.spi.system.backup.BackupSink;
import online.yudream.base.plugin.spi.system.backup.BackupSource;
import online.yudream.base.plugin.spi.system.backup.PluginBackupConflictStrategy;
import online.yudream.base.plugin.spi.system.backup.PluginBackupProvider;
import online.yudream.base.plugin.spi.system.backup.ScopedBackupFile;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 插件备份范围注册表：聚合各已启用插件经 SPI 扩展点注册的 PluginBackupProvider，
 * 供导出/导入编排调用。注册随插件 disable/unload 自动回收，此处无需感知插件生命周期。
 */
@Service
public class PluginBackupScopeRegistry implements PluginBackupScopeSource {

    private final PluginExtensionRegistry extensions;

    public PluginBackupScopeRegistry(PluginExtensionRegistry extensions) {
        this.extensions = extensions;
    }

    @Override
    public List<PluginScopeHandle> scopes() {
        return extensions.registrations(PluginBackupProvider.class).stream()
                .map(registration -> handle(registration.pluginCode(), registration.extension()))
                .toList();
    }

    @Override
    public Optional<PluginScopeHandle> find(String pluginCode, String scopeCode) {
        return extensions.registrations(PluginBackupProvider.class).stream()
                .filter(registration -> registration.pluginCode().equals(pluginCode)
                        && registration.extension().scopeCode().equals(scopeCode))
                .findFirst()
                .map(registration -> handle(registration.pluginCode(), registration.extension()));
    }

    @Override
    public List<String> exportScope(PluginScopeHandle handle, PluginScopeSink sink, Map<String, String> options) {
        try {
            PluginBackupProvider.ExportReport report = provider(handle).export(validatingSink(sink),
                    new BackupExportOptions(options == null ? Map.of() : Map.copyOf(options)));
            return report == null || report.warnings() == null
                    ? List.of()
                    : List.copyOf(report.warnings());
        } catch (online.yudream.base.domain.common.exception.BizException e) {
            throw e;
        } catch (Exception e) {
            throw new online.yudream.base.domain.common.exception.BizException(
                    "插件备份范围导出失败（" + handle.pluginCode() + "/" + handle.scopeCode() + "）：" + e.getMessage());
        }
    }

    @Override
    public void restoreScope(PluginScopeHandle handle, PluginBackupFileSource files, BackupConflictStrategy strategy) {
        PluginBackupConflictStrategy spiStrategy = strategy == BackupConflictStrategy.ARCHIVE_WINS
                ? PluginBackupConflictStrategy.ARCHIVE_WINS
                : PluginBackupConflictStrategy.LOCAL_WINS;
        try {
            provider(handle).restore(wrappingSource(files), spiStrategy);
        } catch (online.yudream.base.domain.common.exception.BizException e) {
            throw e;
        } catch (Exception e) {
            throw new online.yudream.base.domain.common.exception.BizException(
                    "插件备份范围恢复失败（" + handle.pluginCode() + "/" + handle.scopeCode() + "）：" + e.getMessage());
        }
    }

    private PluginScopeHandle handle(String pluginCode, PluginBackupProvider provider) {
        return new PluginScopeHandle(pluginCode, provider.scopeCode(), provider.displayName(),
                provider.description(), provider.defaultSchedule());
    }

    private PluginBackupProvider provider(PluginScopeHandle handle) {
        return extensions.registrations(PluginBackupProvider.class).stream()
                .filter(registration -> registration.pluginCode().equals(handle.pluginCode())
                        && registration.extension().scopeCode().equals(handle.scopeCode()))
                .findFirst()
                .map(PluginExtensionRegistry.ExtensionRegistration::extension)
                .orElseThrow(() -> new online.yudream.base.domain.common.exception.BizException(
                        "插件备份范围当前不可用：" + handle.pluginCode() + "/" + handle.scopeCode()));
    }

    /** 包装插件 sink：宿主统一校验相对路径合法性。 */
    private BackupSink validatingSink(PluginScopeSink sink) {
        return new BackupSink() {
            @Override
            public void putFile(String relativePath, long size, InputStream in) {
                ArchivePaths.validateRelative(relativePath);
                sink.putFile(relativePath, size, in);
            }

            @Override
            public void putText(String relativePath, String content) {
                ArchivePaths.validateRelative(relativePath);
                byte[] bytes = (content == null ? "" : content).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                sink.putFile(relativePath, bytes.length, new java.io.ByteArrayInputStream(bytes));
            }
        };
    }

    private BackupSource wrappingSource(PluginBackupFileSource files) {
        return new BackupSource() {
            @Override
            public List<ScopedBackupFile> files() {
                return files.files().stream()
                        .map(entry -> new ScopedBackupFile(entry.path(), entry.size(), entry.sha256()))
                        .toList();
            }

            @Override
            public InputStream open(String relativePath) {
                ArchivePaths.validateRelative(relativePath);
                return files.open(relativePath);
            }
        };
    }
}
