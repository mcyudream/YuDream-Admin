package online.yudream.base.application.system.backup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.backup.support.BackupDirectorySupport;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.aggregate.BackupPlan;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.enumerate.BackupScopeType;
import online.yudream.base.domain.system.backup.repo.BackupJobRepo;
import online.yudream.base.domain.system.backup.repo.BackupPlanRepo;
import online.yudream.base.domain.system.backup.repo.RemoteTargetRepo;
import online.yudream.base.domain.system.backup.service.BackupArchiveReader;
import online.yudream.base.domain.system.backup.service.BackupArchiveWriter;
import online.yudream.base.domain.system.backup.service.BackupJobRunner;
import online.yudream.base.domain.system.backup.service.BackupRestoreStore;
import online.yudream.base.domain.system.backup.service.CredentialFingerprint;
import online.yudream.base.domain.system.backup.service.PluginBackupScopeSource;
import online.yudream.base.domain.system.backup.service.RemoteBackupStorage;
import online.yudream.base.domain.system.backup.service.SystemDataSnapshotter;
import online.yudream.base.domain.system.backup.service.SystemObjectSnapshotter;
import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;
import online.yudream.base.domain.system.backup.valobj.BackupJobResult;
import online.yudream.base.domain.system.backup.valobj.BackupJobStats;
import online.yudream.base.domain.system.backup.valobj.BackupManifest;
import online.yudream.base.domain.system.backup.valobj.BackupManifestHeader;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;
import online.yudream.base.domain.system.backup.valobj.RemoteEntry;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import online.yudream.base.domain.system.file.valobj.StoredObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 备份任务执行编排：
 * <ul>
 *   <li>导出/异地备份：系统集合（EJSON）+ 对象存储 + 插件范围按 YDBA 归档写出，可推远端并按保留份数清理；</li>
 *   <li>合并导入：按批次比对同标识数据，冲突按策略裁决（本地为准只补缺 / 备份为准覆盖），未安装插件范围跳过并告警；</li>
 *   <li>异地恢复：下载远端归档后走同一合并导入。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupExecutionService implements BackupJobRunner {

    private static final int IMPORT_BATCH = 500;
    private static final int OBJECT_PROGRESS_STEP = 50;
    private static final DateTimeFormatter ARCHIVE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SystemDataSnapshotter dataSnapshotter;
    private final SystemObjectSnapshotter objectSnapshotter;
    private final BackupRestoreStore restoreStore;
    private final PluginBackupScopeSource scopeSource;
    private final BackupArchiveWriter.Factory writerFactory;
    private final BackupArchiveReader.Factory readerFactory;
    private final RemoteBackupStorage.Factory remoteFactory;
    private final RemoteTargetRepo remoteTargetRepo;
    private final BackupPlanRepo planRepo;
    private final CredentialFingerprint fingerprint;
    private final BackupDirectorySupport directories;

    @Value("${yudream.platform.plugin.compatibility.host:1.0.0}")
    private String hostVersion;

    @Override
    public BackupJobResult run(BackupJob job, BackupJobProgress progress) {
        return switch (job.getType()) {
            case EXPORT -> export(job, progress, null);
            case REMOTE_BACKUP -> export(job, progress, targetOf(job.getTargetCode()));
            case IMPORT -> importArchive(Path.of(job.getArchivePath()), progress, job.getStrategy());
            case REMOTE_RESTORE -> restoreRemote(job, progress);
        };
    }

    // ---------------------------------------------------------------- 导出

    private BackupJobResult export(BackupJob job, BackupJobProgress progress, RemoteTarget target) {
        List<BackupScopeRef> scopes = resolveScopes(job.getScopeTags());
        String archiveName = archiveName(job.getPlanCode());
        Path localFile = target == null ? directories.newArchiveFile(archiveName) : directories.newTempFile("push-");
        boolean success = false;
        try {
            List<String> exportWarnings = new ArrayList<>();
            BackupJobStats stats = writeArchive(localFile, scopes, progress, exportWarnings);
            long size = Files.size(localFile);
            if (target != null) {
                progress.update("upload", "正在推送到异地：" + target.getName(), 99);
                remoteFactory.create(target).put(archiveName, Files.newInputStream(localFile), size);
                prune(target, archivePrefix(job.getPlanCode()), retentionOf(job.getPlanCode()));
                success = true;
                progress.update("done", "已推送到异地", 100);
                return new BackupJobResult(archiveName, null, size, stats, List.copyOf(exportWarnings));
            }
            success = true;
            return new BackupJobResult(archiveName, localFile.toAbsolutePath().toString(), size, stats,
                    List.copyOf(exportWarnings));
        } catch (IOException e) {
            throw new BizException("备份归档写入失败：" + e.getMessage());
        } finally {
            if (!success || target != null) {
                directories.deleteQuietly(localFile);
            }
        }
    }

    private BackupJobStats writeArchive(Path localFile, List<BackupScopeRef> scopes, BackupJobProgress progress,
                                        List<String> warnings)
            throws IOException {
        long documents = 0;
        long objects = 0;
        long pluginFiles = 0;
        int collectionCount = 0;
        try (OutputStream out = Files.newOutputStream(localFile);
             BackupArchiveWriter writer = writerFactory.create(out)) {
            boolean includeSystem = scopes.stream().anyMatch(scope -> scope.type() == BackupScopeType.SYSTEM);
            if (includeSystem) {
                List<SystemDataSnapshotter.CollectionSummary> summaries = dataSnapshotter.collections();
                long totalDocs = Math.max(1, summaries.stream().mapToLong(SystemDataSnapshotter.CollectionSummary::count).sum());
                int index = 0;
                for (SystemDataSnapshotter.CollectionSummary summary : summaries) {
                    index++;
                    progress.update("export", "正在备份集合 " + summary.name() + "（" + index + "/" + summaries.size() + "）",
                            (int) Math.min(70, documents * 70 / totalDocs));
                    writer.openCollection(summary.name());
                    long[] heartbeat = {0};
                    long baseDocs = documents;
                    dataSnapshotter.streamCollection(summary.name(), document -> {
                        try {
                            writer.writeCollectionDocument(document.ejson());
                        } catch (IOException e) {
                            throw new BizException("写入集合数据失败：" + summary.name());
                        }
                        heartbeat[0]++;
                        if (heartbeat[0] % 2000 == 0) {
                            progress.update("export", "正在备份集合 " + summary.name(),
                                    (int) Math.min(70, (baseDocs + heartbeat[0]) * 70 / totalDocs));
                        }
                    });
                    writer.closeCollection();
                    documents += heartbeat[0];
                    collectionCount++;
                }
                progress.update("export", "正在备份对象存储…", 72);
                long[] heartbeat = {0};
                objectSnapshotter.streamObjects(object -> exportObject(writer, object, heartbeat, progress));
                objects += heartbeat[0];
            }
            int scopeIndex = 0;
            List<BackupScopeRef> pluginScopes = scopes.stream()
                    .filter(scope -> scope.type() == BackupScopeType.PLUGIN).toList();
            long[] pluginHolder = {0};
            for (BackupScopeRef scope : pluginScopes) {
                scopeIndex++;
                int percent = 75 + scopeIndex * 20 / Math.max(1, pluginScopes.size());
                progress.update("export", "正在备份插件范围 " + scope.tag(), Math.min(95, percent));
                PluginBackupScopeSource.PluginScopeHandle handle = scopeSource.find(scope.pluginCode(), scope.scopeCode())
                        .orElseThrow(() -> new BizException("插件备份范围当前不可用：" + scope.tag()));
                writer.openPluginScope(scope);
                warnings.addAll(scopeSource.exportScope(handle, (path, size, in) -> {
                    try {
                        writer.writePluginFile(path, in, size);
                    } catch (IOException e) {
                        throw new BizException("写入插件备份文件失败：" + path);
                    }
                    pluginHolder[0]++;
                }));
                writer.closePluginScope();
            }
            pluginFiles = pluginHolder[0];
            progress.update("finalize", "正在生成归档清单…", 98);
            writer.finish(new BackupManifestHeader(hostVersion, fingerprint.fingerprint(), scopes,
                    dataSnapshotter.excludedCollections()));
        }
        return new BackupJobStats(collectionCount, documents, objects, pluginFiles, 0, 0, 0);
    }

    private void exportObject(BackupArchiveWriter writer, StoredObject object, long[] heartbeat,
                              BackupJobProgress progress) {
        try (InputStream in = object.inputStream()) {
            writer.writeObject(object.objectKey(), in, object.contentLength() == null ? 0 : object.contentLength());
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("读取对象失败：" + object.objectKey());
        }
        heartbeat[0]++;
        if (heartbeat[0] % OBJECT_PROGRESS_STEP == 0) {
            progress.update("export", "已备份对象 " + heartbeat[0] + " 个", 74);
        }
    }

    private void prune(RemoteTarget target, String prefix, int retention) {
        if (retention < 1) {
            return;
        }
        RemoteBackupStorage storage = remoteFactory.create(target);
        List<RemoteEntry> entries = new ArrayList<>(storage.list(prefix));
        entries.sort((a, b) -> Long.compare(
                b.modifiedAtMillis() == null ? 0 : b.modifiedAtMillis(),
                a.modifiedAtMillis() == null ? 0 : a.modifiedAtMillis()));
        for (int i = retention; i < entries.size(); i++) {
            String name = entries.get(i).name();
            try {
                storage.delete(name);
                log.info("异地备份超过保留份数，已删除：{}/{}", target.getCode(), name);
            } catch (BizException e) {
                log.warn("清理异地旧备份失败：{}/{}（{}）", target.getCode(), name, e.getMessage());
            }
        }
    }

    private int retentionOf(String planCode) {
        if (planCode == null) {
            return BackupPlan.DEFAULT_RETENTION;
        }
        return planRepo.findByCode(planCode).map(BackupPlan::getRetentionCount).orElse(BackupPlan.DEFAULT_RETENTION);
    }

    // ---------------------------------------------------------------- 合并导入

    private BackupJobResult importArchive(Path archiveFile, BackupJobProgress progress,
                                          BackupConflictStrategy strategy) {
        List<String> warnings = new ArrayList<>();
        Counters counters = new Counters();
        try (BackupArchiveReader reader = readerFactory.open(archiveFile)) {
            BackupManifest manifest = reader.manifest();
            manifest.ensureSupported();
            if (strategy == null) {
                throw new BizException("请先选择合并策略（以哪边为准）");
            }
            checkMasterKey(manifest, warnings);
            long totalDocs = Math.max(1, manifest.collections().stream()
                    .mapToLong(BackupManifest.ManifestCollection::count).sum());
            long totalUnits = totalDocs + Math.max(1, manifest.objectCount());
            long[] done = {0};
            for (BackupManifest.ManifestCollection collection : manifest.collections()) {
                progress.update("import", "正在合并集合 " + collection.name(),
                        (int) Math.min(70, done[0] * 70 / totalUnits));
                counters.collections++;
                mergeCollection(reader, collection.name(), strategy, counters, done, totalUnits);
            }
            progress.update("import", "正在合并对象存储…", 72);
            mergeObjects(reader, strategy, counters, progress);
            mergePluginScopes(reader, manifest, strategy, counters, warnings, progress);
        } catch (IOException e) {
            throw new BizException("读取备份归档失败：" + e.getMessage());
        } finally {
            // 导入完成后清理暂存归档（上传落盘与异地下载共用），失败也不留孤儿临时文件
            directories.deleteQuietly(archiveFile);
        }
        progress.update("finalize", "合并完成", 100);
        return new BackupJobResult(null, null, null, counters.toStats(), List.copyOf(warnings));
    }

    private void mergeCollection(BackupArchiveReader reader, String collection,
                                 BackupConflictStrategy strategy, Counters counters,
                                 long[] done, long totalUnits) {
        List<SnapshotDocument> batch = new ArrayList<>(IMPORT_BATCH);
        reader.streamCollection(collection, document -> {
            batch.add(document);
            if (batch.size() >= IMPORT_BATCH) {
                flushBatch(collection, batch, strategy, counters);
                done[0] += batch.size();
                batch.clear();
            }
        });
        if (!batch.isEmpty()) {
            flushBatch(collection, batch, strategy, counters);
            done[0] += batch.size();
        }
    }

    private void flushBatch(String collection, List<SnapshotDocument> batch,
                            BackupConflictStrategy strategy, Counters counters) {
        Set<String> ids = new LinkedHashSet<>();
        batch.forEach(document -> ids.add(document.id()));
        Set<String> existing = restoreStore.existingDocumentIds(collection, ids);
        long conflicts = batch.stream().filter(document -> existing.contains(document.id())).count();
        if (strategy == BackupConflictStrategy.ARCHIVE_WINS) {
            restoreStore.writeDocuments(collection, batch, true);
            counters.inserted += batch.size() - conflicts;
        } else {
            List<SnapshotDocument> missing = batch.stream()
                    .filter(document -> !existing.contains(document.id())).toList();
            restoreStore.writeDocuments(collection, missing, false);
            counters.inserted += missing.size();
        }
        counters.conflicts += conflicts;
        counters.documents += batch.size();
    }

    private void mergeObjects(BackupArchiveReader reader, BackupConflictStrategy strategy,
                              Counters counters, BackupJobProgress progress) {
        List<ArchiveFileEntry> objects = reader.objects();
        Set<String> existingKeys = existingKeysFor(objects);
        long index = 0;
        for (ArchiveFileEntry entry : objects) {
            boolean exists = existingKeys.contains(entry.path());
            if (strategy == BackupConflictStrategy.ARCHIVE_WINS || !exists) {
                try (InputStream in = reader.openObject(entry.path())) {
                    restoreStore.writeObject(entry.path(), in, entry.size());
                } catch (IOException e) {
                    throw new BizException("读取备份对象失败：" + entry.path());
                }
                counters.inserted++;
            } else {
                counters.skipped++;
            }
            if (exists) {
                counters.conflicts++;
            }
            counters.objects++;
            index++;
            if (index % OBJECT_PROGRESS_STEP == 0) {
                progress.update("import", "已合并对象 " + index + "/" + objects.size(),
                        72 + (int) (18 * index / Math.max(1, objects.size())));
            }
        }
    }

    private Set<String> existingKeysFor(List<ArchiveFileEntry> objects) {
        if (objects.isEmpty()) {
            return Set.of();
        }
        List<String> keys = objects.stream().map(ArchiveFileEntry::path).toList();
        Set<String> existing = new HashSet<>();
        for (int from = 0; from < keys.size(); from += IMPORT_BATCH) {
            existing.addAll(restoreStore.existingObjectKeys(keys.subList(from, Math.min(from + IMPORT_BATCH, keys.size()))));
        }
        return existing;
    }

    private void mergePluginScopes(BackupArchiveReader reader, BackupManifest manifest,
                                   BackupConflictStrategy strategy, Counters counters,
                                   List<String> warnings, BackupJobProgress progress) {
        int scopeIndex = 0;
        for (BackupManifest.ManifestPluginScope scope : manifest.pluginScopes()) {
            scopeIndex++;
            progress.update("import", "正在合并插件范围 " + scope.pluginCode() + "/" + scope.scopeCode(),
                    90 + scopeIndex * 8 / Math.max(1, manifest.pluginScopes().size()));
            Optional<PluginBackupScopeSource.PluginScopeHandle> handle =
                    scopeSource.find(scope.pluginCode(), scope.scopeCode());
            if (handle.isEmpty()) {
                counters.skipped += scope.fileCount();
                warnings.add("插件 " + scope.pluginCode() + "/" + scope.scopeCode()
                        + " 未安装或未提供备份范围，其 " + scope.fileCount() + " 个文件已跳过");
                continue;
            }
            List<ArchiveFileEntry> files = reader.pluginFiles(scope.pluginCode(), scope.scopeCode());
            PluginBackupScopeSource.PluginBackupFileSource source = new PluginBackupScopeSource.PluginBackupFileSource() {
                @Override
                public List<ArchiveFileEntry> files() {
                    return files;
                }

                @Override
                public InputStream open(String path) {
                    return reader.openPluginFile(scope.pluginCode(), scope.scopeCode(), path);
                }
            };
            scopeSource.restoreScope(handle.get(), source, strategy);
        }
    }

    private void checkMasterKey(BackupManifest manifest, List<String> warnings) {
        String current = fingerprint.fingerprint();
        if (!StringUtils.hasText(current) || !StringUtils.hasText(manifest.masterKeyFingerprint())) {
            return;
        }
        if (!current.equals(manifest.masterKeyFingerprint())) {
            warnings.add("备份来自不同主密钥（指纹 " + manifest.masterKeyFingerprint()
                    + " ≠ 当前 " + current + "），其中加密存储的凭据（邮箱/存储/目标密码等）可能无法解密，需在导入后重新配置");
        }
    }

    // ---------------------------------------------------------------- 异地恢复

    private BackupJobResult restoreRemote(BackupJob job, BackupJobProgress progress) {
        RemoteTarget target = targetOf(job.getTargetCode());
        Path temp = directories.newTempFile("restore-");
        try {
            progress.update("download", "正在从异地下载归档…", 3);
            remoteFactory.create(target).fetch(job.getArchiveName(), temp);
            return importArchive(temp, progress, job.getStrategy());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("从异地恢复失败：" + e.getMessage());
        }
        // temp 由 importArchive 统一清理
    }

    // ---------------------------------------------------------------- 公共

    private RemoteTarget targetOf(String targetCode) {
        if (!StringUtils.hasText(targetCode)) {
            throw new BizException("备份任务未指定异地目标");
        }
        return remoteTargetRepo.findByCode(targetCode)
                .orElseThrow(() -> new BizException("异地目标不存在：" + targetCode));
    }

    private List<BackupScopeRef> resolveScopes(List<String> scopeTags) {
        if (scopeTags == null || scopeTags.isEmpty()) {
            return List.of(BackupScopeRef.system());
        }
        LinkedHashSet<BackupScopeRef> scopes = new LinkedHashSet<>();
        scopeTags.forEach(tag -> scopes.add(BackupScopeRef.parse(tag)));
        return List.copyOf(scopes);
    }

    static String archivePrefix(String planCode) {
        return "yudream-backup-" + (planCode == null || planCode.isBlank() ? "" : planCode + "-");
    }

    static String archiveName(String planCode) {
        return archivePrefix(planCode) + LocalDateTime.now().format(ARCHIVE_TIME) + ".zip";
    }

    private static final class Counters {
        private long collections;
        private long documents;
        private long objects;
        private long inserted;
        private long conflicts;
        private long skipped;

        private BackupJobStats toStats() {
            return new BackupJobStats(collections, documents, objects, 0, inserted, conflicts, skipped);
        }
    }
}
