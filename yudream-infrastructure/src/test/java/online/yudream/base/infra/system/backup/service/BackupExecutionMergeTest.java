package online.yudream.base.infra.system.backup.service;

import online.yudream.base.application.system.backup.service.BackupExecutionService;
import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.enumerate.BackupJobType;
import online.yudream.base.domain.system.backup.service.BackupRestoreStore;
import online.yudream.base.domain.system.backup.service.CredentialFingerprint;
import online.yudream.base.domain.system.backup.service.PluginBackupScopeSource;
import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;
import online.yudream.base.domain.system.backup.valobj.BackupJobResult;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合并导入策略编排测试：本地为准只补缺，备份为准覆盖同名，插件范围按策略回调恢复。
 */
class BackupExecutionMergeTest {

    @TempDir
    Path tempDir;

    @Test
    void localWinsKeepsExistingAndFillsMissing() throws Exception {
        Path archive = buildArchive();
        MemoryRestoreStore store = localStore();
        RecordingScopeSource scopes = new RecordingScopeSource();
        BackupExecutionService service = new BackupExecutionService(
                null, null, store, scopes, null, new ZipBackupArchiveReader.Factory(),
                null, null, null, () -> "fedcba9876543210", new online.yudream.base.application.system.backup.support.BackupDirectorySupport("target/test-backup"));

        BackupJob job = job(archive, BackupConflictStrategy.LOCAL_WINS);
        BackupJobResult result = service.run(job, (phase, message, percent) -> { });

        assertEquals(2, result.stats().insertedCount());
        assertEquals(2, result.stats().conflictCount());
        assertEquals(1, result.stats().skippedCount());
        assertTrue(store.collections.get("sysSetting").get("a").contains("old-a"));
        assertTrue(store.collections.get("sysSetting").get("b").contains("b-new"));
        assertEquals(1, store.writtenObjects.size());
        assertTrue(store.writtenObjects.contains("objects/key2.bin"));
        assertEquals(1, scopes.restoreCalls.get());
        assertEquals(BackupConflictStrategy.LOCAL_WINS, scopes.lastStrategy());
    }

    @Test
    void archiveWinsReplacesExistingAndKeepsLocalOnly() throws Exception {
        Path archive = buildArchive();
        MemoryRestoreStore store = localStore();
        RecordingScopeSource scopes = new RecordingScopeSource();
        BackupExecutionService service = new BackupExecutionService(
                null, null, store, scopes, null, new ZipBackupArchiveReader.Factory(),
                null, null, null, () -> "fedcba9876543210", new online.yudream.base.application.system.backup.support.BackupDirectorySupport("target/test-backup"));

        BackupJob job = job(archive, BackupConflictStrategy.ARCHIVE_WINS);
        BackupJobResult result = service.run(job, (phase, message, percent) -> { });

        assertEquals(3, result.stats().insertedCount());
        assertEquals(2, result.stats().conflictCount());
        assertTrue(store.collections.get("sysSetting").get("a").contains("a-new"));
        assertTrue(store.collections.get("sysSetting").get("b").contains("b-new"));
        assertTrue(store.collections.get("sysSetting").get("c").contains("local-only"));
        assertEquals(2, store.writtenObjects.size());
        assertEquals(BackupConflictStrategy.ARCHIVE_WINS, scopes.lastStrategy());
    }

    @Test
    void missingPluginScopeIsSkippedWithWarning() throws Exception {
        Path archive = buildArchive();
        MemoryRestoreStore store = localStore();
        RecordingScopeSource scopes = new RecordingScopeSource(false);
        BackupExecutionService service = new BackupExecutionService(
                null, null, store, scopes, null, new ZipBackupArchiveReader.Factory(),
                null, null, null, () -> "fedcba9876543210", new online.yudream.base.application.system.backup.support.BackupDirectorySupport("target/test-backup"));

        BackupJob job = job(archive, BackupConflictStrategy.LOCAL_WINS);
        BackupJobResult result = service.run(job, (phase, message, percent) -> { });

        assertEquals(2, result.stats().skippedCount());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("mcpanel/server-data"));
        assertEquals(0, scopes.restoreCalls.get());
    }

    // ---------------------------------------------------------------- 桩与夹具

    private BackupJob job(Path archive, BackupConflictStrategy strategy) {
        BackupJob job = BackupJob.create(BackupJobType.IMPORT, online.yudream.base.domain.system.backup.enumerate.BackupJobTrigger.MANUAL,
                List.of(), strategy, null, null, null);
        job.setArchivePath(archive.toString());
        return job;
    }

    private Path buildArchive() throws Exception {
        Path archive = tempDir.resolve("merge-" + System.nanoTime() + ".zip");
        try (ZipBackupArchiveWriter writer = new ZipBackupArchiveWriter(Files.newOutputStream(archive))) {
            writer.openCollection("sysSetting");
            writer.writeCollectionDocument("{\"_id\":\"a\",\"v\":\"a-new\"}");
            writer.writeCollectionDocument("{\"_id\":\"b\",\"v\":\"b-new\"}");
            writer.closeCollection();
            writer.writeObject("objects/key1.bin", new ByteArrayInputStream("k1".getBytes(StandardCharsets.UTF_8)), 2);
            writer.writeObject("objects/key2.bin", new ByteArrayInputStream("k2".getBytes(StandardCharsets.UTF_8)), 2);
            writer.openPluginScope(BackupScopeRef.plugin("mcpanel", "server-data", "服务器数据"));
            writer.writePluginFile("data.txt", new ByteArrayInputStream("d".getBytes(StandardCharsets.UTF_8)), 1);
            writer.closePluginScope();
            writer.finish(new online.yudream.base.domain.system.backup.valobj.BackupManifestHeader(
                    "test", "fedcba9876543210", List.of(BackupScopeRef.system()), List.of()));
        }
        return archive;
    }

    private MemoryRestoreStore localStore() {
        MemoryRestoreStore store = new MemoryRestoreStore();
        store.collections.put("sysSetting", new HashMap<>(Map.of(
                "a", "{\"_id\":\"a\",\"v\":\"old-a\"}",
                "c", "{\"_id\":\"c\",\"v\":\"local-only\"}")));
        store.objectKeys.add("objects/key1.bin");
        return store;
    }

    /** 内存还原存储：记录写入语义供断言。 */
    private static final class MemoryRestoreStore implements BackupRestoreStore {
        private final Map<String, Map<String, String>> collections = new HashMap<>();
        private final Set<String> objectKeys = new HashSet<>();
        private final List<String> writtenObjects = new ArrayList<>();

        @Override
        public Set<String> existingDocumentIds(String collection, Set<String> candidateIds) {
            Map<String, String> existing = collections.getOrDefault(collection, Map.of());
            Set<String> result = new HashSet<>();
            for (String id : candidateIds) {
                if (existing.containsKey(id)) {
                    result.add(id);
                }
            }
            return result;
        }

        @Override
        public void writeDocuments(String collection, Collection<SnapshotDocument> documents, boolean replaceExisting) {
            Map<String, String> target = collections.computeIfAbsent(collection, ignored -> new HashMap<>());
            for (SnapshotDocument document : documents) {
                if (replaceExisting) {
                    target.put(document.id(), document.ejson());
                } else {
                    target.putIfAbsent(document.id(), document.ejson());
                }
            }
        }

        @Override
        public Set<String> existingObjectKeys(Collection<String> keys) {
            Set<String> result = new HashSet<>();
            for (String key : keys) {
                if (objectKeys.contains(key)) {
                    result.add(key);
                }
            }
            return result;
        }

        @Override
        public void writeObject(String objectKey, java.io.InputStream in, long size) {
            objectKeys.add(objectKey);
            writtenObjects.add(objectKey);
        }
    }

    /** 记录恢复回调的插件范围来源。 */
    private static final class RecordingScopeSource implements PluginBackupScopeSource {
        private final boolean available;
        private final AtomicInteger restoreCalls = new AtomicInteger();
        private volatile BackupConflictStrategy lastStrategy;

        private RecordingScopeSource() {
            this(true);
        }

        private RecordingScopeSource(boolean available) {
            this.available = available;
        }

        BackupConflictStrategy lastStrategy() {
            return lastStrategy;
        }

        @Override
        public List<PluginScopeHandle> scopes() {
            return List.of();
        }

        @Override
        public Optional<PluginScopeHandle> find(String pluginCode, String scopeCode) {
            if (available && "mcpanel".equals(pluginCode) && "server-data".equals(scopeCode)) {
                return Optional.of(new PluginScopeHandle(pluginCode, scopeCode, "服务器数据", "", ""));
            }
            return Optional.empty();
        }

        @Override
        public List<String> exportScope(PluginScopeHandle handle, PluginScopeSink sink,
                                        java.util.Map<String, String> options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void restoreScope(PluginScopeHandle handle, PluginBackupFileSource files, BackupConflictStrategy strategy) {
            restoreCalls.incrementAndGet();
            lastStrategy = strategy;
        }
    }
}
