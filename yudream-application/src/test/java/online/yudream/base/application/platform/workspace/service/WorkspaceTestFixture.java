package online.yudream.base.application.platform.workspace.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.service.ChatQuotaAppService;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.workspace.aggregate.UserWorkspace;
import online.yudream.base.domain.platform.workspace.aggregate.WorkspaceFile;
import online.yudream.base.domain.platform.workspace.repo.UserWorkspaceRepo;
import online.yudream.base.domain.platform.workspace.repo.WorkspaceFileRepo;
import online.yudream.base.domain.platform.workspace.repo.WorkspaceFileVersionRepo;
import online.yudream.base.domain.platform.workspace.valobj.WorkspaceFileVersion;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.domain.system.file.valobj.StoredObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 内存版仓储与对象存储：验证工作区规则而不依赖 Mongo/S3。 */
public final class WorkspaceTestFixture {

    public final AtomicLong ids = new AtomicLong(100);
    public final Map<Long, WorkspaceFile> files = new LinkedHashMap<>();
    public final Map<Long, WorkspaceFileVersion> versions = new LinkedHashMap<>();
    public final Map<String, byte[]> objects = new HashMap<>();
    public final Map<String, String> config = new HashMap<>();
    public final ChatQuotaAppService quotas = mock(ChatQuotaAppService.class);
    public final FileAppService fileAppService = mock(FileAppService.class);
    public final WorkspaceAppService service;

    public WorkspaceTestFixture() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        when(capabilities.enabled(anyString())).thenReturn(true);
        CapabilityModule module = mock(CapabilityModule.class);
        when(module.getConfig()).thenReturn(config);
        CapabilityModuleRepo modules = mock(CapabilityModuleRepo.class);
        when(modules.findByCode("ai")).thenReturn(Optional.of(module));
        when(quotas.unlimited(any())).thenReturn(false);
        service = new WorkspaceAppService(new Workspaces(), new Files(), new Versions(), new Storage(), capabilities, modules, quotas, fileAppService);
    }

    public String text(String objectKey) {
        return new String(objects.get(objectKey), java.nio.charset.StandardCharsets.UTF_8);
    }

    private final class Workspaces implements UserWorkspaceRepo {
        private final Map<Long, UserWorkspace> byOwner = new HashMap<>();

        public Optional<UserWorkspace> findByOwner(Long ownerUserId) {
            return Optional.ofNullable(byOwner.get(ownerUserId));
        }

        public UserWorkspace getOrCreate(Long ownerUserId) {
            return byOwner.computeIfAbsent(ownerUserId, owner -> {
                UserWorkspace workspace = UserWorkspace.create(owner);
                workspace.setId(ids.incrementAndGet());
                return workspace;
            });
        }
    }

    private final class Files implements WorkspaceFileRepo {
        public List<WorkspaceFile> listActive(Long workspaceId) {
            return files.values().stream().filter(f -> f.getWorkspaceId().equals(workspaceId) && !f.trashed())
                    .sorted(Comparator.comparing(WorkspaceFile::getPath)).toList();
        }

        public List<WorkspaceFile> listTrash(Long workspaceId) {
            return files.values().stream().filter(f -> f.getWorkspaceId().equals(workspaceId) && f.trashed()).toList();
        }

        public Optional<WorkspaceFile> findActive(Long workspaceId, String path) {
            return listActive(workspaceId).stream().filter(f -> f.getPath().equals(path)).findFirst();
        }

        public Optional<WorkspaceFile> findById(Long id) {
            return Optional.ofNullable(files.get(id));
        }

        public WorkspaceFile save(WorkspaceFile file) {
            if (file.getId() == null) {
                file.setId(ids.incrementAndGet());
            }
            files.put(file.getId(), file);
            return file;
        }

        public void delete(Long id) {
            files.remove(id);
        }

        public List<WorkspaceFile> findTrashedBefore(LocalDateTime cutoff, int limit) {
            return files.values().stream().filter(f -> f.trashed() && f.getDeletedAt().isBefore(cutoff)).limit(limit).toList();
        }

        public List<WorkspaceFile> findStaleFilesUnder(String directory, LocalDateTime cutoff, int limit) {
            return files.values().stream().filter(f -> !f.trashed() && !f.directory() && f.getPath().startsWith(directory + "/")
                    && f.getContentUpdatedAt().isBefore(cutoff)).limit(limit).toList();
        }
    }

    private final class Versions implements WorkspaceFileVersionRepo {
        public Long nextId() {
            return ids.incrementAndGet();
        }

        public WorkspaceFileVersion save(WorkspaceFileVersion version) {
            versions.put(version.id(), version);
            return version;
        }

        public Optional<WorkspaceFileVersion> findById(Long id) {
            return Optional.ofNullable(versions.get(id));
        }

        public List<WorkspaceFileVersion> listByFile(Long fileId) {
            return versions.values().stream().filter(v -> v.fileId().equals(fileId))
                    .sorted(Comparator.comparingInt(WorkspaceFileVersion::revision).reversed()).toList();
        }

        public void delete(Long id) {
            versions.remove(id);
        }

        public long usedBytes(Long workspaceId) {
            return versions.values().stream().filter(v -> v.workspaceId().equals(workspaceId)).mapToLong(WorkspaceFileVersion::size).sum();
        }
    }

    private final class Storage implements ObjectStorage {
        public String bucket() {
            return "test";
        }

        public String put(String objectKey, InputStream inputStream, long contentLength, String contentType) {
            try {
                objects.put(objectKey, inputStream.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return objectKey;
        }

        public StoredObject get(String objectKey) {
            byte[] data = objects.get(objectKey);
            return new StoredObject(objectKey, "application/octet-stream", (long) data.length, new ByteArrayInputStream(data));
        }

        public void delete(String objectKey) {
            objects.remove(objectKey);
        }
    }
}
