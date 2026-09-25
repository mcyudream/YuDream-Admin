package online.yudream.base.application.system.backup.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.backup.assembler.BackupAppAssembler;
import online.yudream.base.application.system.backup.assembler.BackupAppAssembler.PluginScopeSourceHandle;
import online.yudream.base.application.system.backup.dto.BackupAnalysisDTO;
import online.yudream.base.application.system.backup.dto.BackupArchiveDownloadDTO;
import online.yudream.base.application.system.backup.dto.BackupJobDTO;
import online.yudream.base.application.system.backup.dto.BackupScopeDTO;
import online.yudream.base.application.system.backup.support.BackupDirectorySupport;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.enumerate.BackupJobStatus;
import online.yudream.base.domain.system.backup.enumerate.BackupJobTrigger;
import online.yudream.base.domain.system.backup.enumerate.BackupJobType;
import online.yudream.base.domain.system.backup.repo.BackupJobRepo;
import online.yudream.base.domain.system.backup.service.BackupArchiveReader;
import online.yudream.base.domain.system.backup.service.BackupRestoreStore;
import online.yudream.base.domain.system.backup.service.CredentialFingerprint;
import online.yudream.base.domain.system.backup.service.PluginBackupScopeSource;
import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;
import online.yudream.base.domain.system.backup.valobj.BackupManifest;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 备份归档应用服务：范围清单、全量导出入队、归档分析预览、合并导入队、本机归档下载与任务管理。
 */
@Service
@RequiredArgsConstructor
public class BackupArchiveAppService {

    private static final int ANALYZE_BATCH = 500;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final BackupJobRepo jobRepo;
    private final BackupArchiveReader.Factory readerFactory;
    private final BackupRestoreStore restoreStore;
    private final PluginBackupScopeSource scopeSource;
    private final CredentialFingerprint fingerprint;
    private final BackupDirectorySupport directories;

    /** 备份范围清单：系统范围 + 当前可用的插件范围。 */
    public List<BackupScopeDTO> scopes() {
        List<BackupScopeDTO> scopes = new ArrayList<>();
        scopes.add(BackupAppAssembler.toSystemScopeDTO());
        scopeSource.scopes().forEach(handle -> scopes.add(BackupAppAssembler.toScopeDTO(PluginScopeSourceHandle.of(handle))));
        return scopes;
    }

    /** 创建全量导出任务并排队。 */
    public BackupJobDTO createExportJob(List<String> scopeTags) {
        List<String> tags = normalizeScopeTags(scopeTags);
        BackupJob job = BackupJob.create(BackupJobType.EXPORT, BackupJobTrigger.MANUAL, tags,
                null, null, null, null);
        return toDTO(jobRepo.save(job));
    }

    /** 同步分析归档：按集合统计缺失/冲突数量，供管理员在执行合并前确认策略。 */
    public BackupAnalysisDTO analyze(String filename, InputStream in, long size) {
        Path temp = directories.newTempFile("analyze-");
        try {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return analyze(temp);
        } catch (IOException e) {
            throw new BizException("保存待分析归档失败：" + e.getMessage());
        } finally {
            directories.deleteQuietly(temp);
        }
    }

    public BackupAnalysisDTO analyze(Path archiveFile) {
        List<String> warnings = new ArrayList<>();
        try (BackupArchiveReader reader = readerFactory.open(archiveFile)) {
            BackupManifest manifest = reader.manifest();
            manifest.ensureSupported();
            List<BackupAnalysisDTO.CollectionAnalysis> collections = new ArrayList<>();
            for (BackupManifest.ManifestCollection collection : manifest.collections()) {
                Set<String> ids = new HashSet<>();
                reader.streamCollection(collection.name(),
                        document -> ids.add(document.id()));
                Set<String> existing = restoreStore.existingDocumentIds(collection.name(), ids);
                long conflict = existing.size();
                collections.add(new BackupAnalysisDTO.CollectionAnalysis(
                        collection.name(), collection.count(), collection.count() - conflict, conflict));
            }
            List<ArchiveFileEntry> objects = reader.objects();
            Set<String> existingKeys = new HashSet<>();
            for (int from = 0; from < objects.size(); from += ANALYZE_BATCH) {
                existingKeys.addAll(restoreStore.existingObjectKeys(
                        objects.subList(from, Math.min(from + ANALYZE_BATCH, objects.size()))
                                .stream().map(ArchiveFileEntry::path).toList()));
            }
            long objectConflict = existingKeys.size();
            long totalBytes = objects.stream().mapToLong(ArchiveFileEntry::size).sum();
            List<BackupAnalysisDTO.PluginScopeAnalysis> pluginScopes = manifest.pluginScopes().stream()
                    .map(scope -> new BackupAnalysisDTO.PluginScopeAnalysis(
                            scope.pluginCode(), scope.scopeCode(),
                            scope.pluginCode() + "/" + scope.scopeCode(),
                            scope.fileCount(),
                            scopeSource.find(scope.pluginCode(), scope.scopeCode()).isPresent()))
                    .toList();
            String currentFingerprint = fingerprint.fingerprint();
            boolean keyMatch = !StringUtils.hasText(manifest.masterKeyFingerprint())
                    || !StringUtils.hasText(currentFingerprint)
                    || currentFingerprint.equals(manifest.masterKeyFingerprint());
            if (!keyMatch) {
                warnings.add("备份来自不同主密钥，其中加密存储的凭据可能无法解密，导入后需重新配置");
            }
            return new BackupAnalysisDTO(manifest.hostVersion(), manifest.createdAt(),
                    manifest.masterKeyFingerprint(), keyMatch, List.copyOf(collections),
                    new BackupAnalysisDTO.ObjectAnalysis(objects.size(), objects.size() - objectConflict,
                            objectConflict, totalBytes),
                    pluginScopes, List.copyOf(warnings));
        } catch (IOException e) {
            throw new BizException("读取备份归档失败：" + e.getMessage());
        }
    }

    /** 上传归档并创建合并导入任务（先落临时文件，执行完成后清理）。 */
    public BackupJobDTO createImportJob(String filename, InputStream in, long size, BackupConflictStrategy strategy) {
        if (strategy == null) {
            throw new BizException("请先选择合并策略（以哪边为准）");
        }
        Path temp = directories.newTempFile("import-");
        try {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            directories.deleteQuietly(temp);
            throw new BizException("保存上传归档失败：" + e.getMessage());
        }
        String name = StringUtils.hasText(filename) ? filename : "upload-" + stamp() + ".zip";
        BackupJob job = BackupJob.create(BackupJobType.IMPORT, BackupJobTrigger.MANUAL,
                List.of(), strategy, null, null, null);
        job.setArchiveName(name);
        job.setArchivePath(temp.toString());
        job.setArchiveSize(size);
        return toDTO(jobRepo.save(job));
    }

    public List<BackupJobDTO> jobs(int limit) {
        return BackupAppAssembler.toJobDTOs(jobRepo.findRecent(Math.min(Math.max(limit, 1), 200)));
    }

    public BackupJobDTO job(Long id) {
        return toDTO(jobRepo.findById(id).orElseThrow(() -> new BizException("备份任务不存在")));
    }

    public void deleteJob(Long id) {
        BackupJob job = jobRepo.findById(id).orElseThrow(() -> new BizException("备份任务不存在"));
        if (job.getStatus() == BackupJobStatus.RUNNING || job.getStatus() == BackupJobStatus.QUEUED) {
            throw new BizException("任务正在执行，暂不能删除");
        }
        if (StringUtils.hasText(job.getArchivePath())) {
            directories.deleteQuietly(Path.of(job.getArchivePath()));
        }
        jobRepo.deleteById(id);
    }

    /** 本机归档下载信息（仅成功导出任务有本地归档）。 */
    public BackupArchiveDownloadDTO archiveDownload(Long id) {
        BackupJob job = jobRepo.findById(id).orElseThrow(() -> new BizException("备份任务不存在"));
        if (job.getStatus() != BackupJobStatus.SUCCEEDED || !StringUtils.hasText(job.getArchivePath())) {
            throw new BizException("该任务没有可下载的本机归档");
        }
        Path file = Path.of(job.getArchivePath());
        if (!Files.exists(file)) {
            throw new BizException("归档文件已被清理，请重新导出");
        }
        try {
            return new BackupArchiveDownloadDTO(job.getArchiveName(), file.toString(), Files.size(file));
        } catch (Exception e) {
            throw new BizException("读取归档信息失败：" + e.getMessage());
        }
    }

    private List<String> normalizeScopeTags(List<String> scopeTags) {
        if (scopeTags == null || scopeTags.isEmpty()) {
            return List.of(BackupScopeRef.system().tag());
        }
        Set<String> tags = new LinkedHashSet<>();
        scopeTags.forEach(tag -> tags.add(BackupScopeRef.parse(tag).tag()));
        return List.copyOf(tags);
    }

    private static String stamp() {
        return java.time.LocalDateTime.now().format(STAMP);
    }

    private static BackupJobDTO toDTO(BackupJob job) {
        return BackupAppAssembler.toJobDTO(job);
    }
}
