package online.yudream.base.application.platform.workspace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.service.ChatQuotaAppService;
import online.yudream.base.application.platform.workspace.assembler.WorkspaceAssembler;
import online.yudream.base.application.platform.workspace.cmd.WorkspaceConfigCmd;
import online.yudream.base.application.platform.workspace.dto.WorkspaceConfigDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceDownloadDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceEntryDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceFileContentDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceOverviewDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceTrashDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceVersionDTO;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.workspace.aggregate.UserWorkspace;
import online.yudream.base.domain.platform.workspace.aggregate.WorkspaceFile;
import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceActor;
import online.yudream.base.domain.platform.workspace.repo.UserWorkspaceRepo;
import online.yudream.base.domain.platform.workspace.repo.WorkspaceFileRepo;
import online.yudream.base.domain.platform.workspace.repo.WorkspaceFileVersionRepo;
import online.yudream.base.domain.platform.workspace.valobj.WorkspaceFileVersion;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.domain.system.file.valobj.StoredObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 用户工作区：元数据（目录树、版本、回收站、来源）在数据库，文件内容在对象存储
 * （workspaces/&lt;wsId&gt;/objects/&lt;versionId&gt;，改名/移动只改元数据）。
 * 覆盖保留最近 {@value #KEEP_VERSIONS} 个版本；删除进回收站 {@value #TRASH_DAYS} 天；tmp/ 按配置天数自动清理；
 * 配额按全部版本字节计，管理员不限。界面端点与 AI 工具（{@link WorkspaceAccess}）共用这里的规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class WorkspaceAppService {

    public static final String TMP_DIR = "tmp";
    static final long DEFAULT_QUOTA_MB = 500;
    static final int DEFAULT_TMP_DAYS = 7;
    static final int TRASH_DAYS = 30;
    static final int KEEP_VERSIONS = 10;
    static final long MAX_FILE_BYTES = 100L * 1024 * 1024;
    /** 预览/AI 读取时整体载入的上限，超出只能下载 */
    static final long PREVIEW_BYTES = 2L * 1024 * 1024;
    private static final int PURGE_BATCH = 200;
    private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "markdown", "txt", "json", "jsonl", "yaml", "yml", "toml",
            "csv", "tsv", "xml", "html", "htm", "css", "scss", "less", "js", "mjs", "cjs", "ts", "tsx", "jsx", "vue",
            "py", "java", "kt", "go", "rs", "rb", "php", "sh", "bash", "sql", "ini", "conf", "log", "env", "c", "h", "cpp", "hpp");

    private final UserWorkspaceRepo workspaces;
    private final WorkspaceFileRepo files;
    private final WorkspaceFileVersionRepo versions;
    private final ObjectStorage storage;
    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModules;
    private final ChatQuotaAppService quotas;
    private final FileAppService fileAppService;

    public WorkspaceOverviewDTO overview(Long userId) {
        UserWorkspace workspace = workspace(userId);
        List<WorkspaceEntryDTO> entries = files.listActive(workspace.getId()).stream().map(WorkspaceAssembler::entry).toList();
        Long quota = quotaBytes(userId);
        return new WorkspaceOverviewDTO(String.valueOf(workspace.getId()), workspace.uri(), entries,
                versions.usedBytes(workspace.getId()), quota, tmpRetentionDays(), TRASH_DAYS);
    }

    public WorkspaceFileContentDTO read(Long userId, String path) {
        return preview(workspace(userId), WorkspacePaths.file(path), Integer.MAX_VALUE);
    }

    public WorkspaceDownloadDTO download(Long userId, String path) {
        WorkspaceFile file = requireFile(workspace(userId), WorkspacePaths.file(path));
        StoredObject object = object(file);
        return new WorkspaceDownloadDTO(WorkspacePaths.fileName(file.getPath()), file.getContentType(), file.getSize(),
                object.inputStream());
    }

    public WorkspaceEntryDTO writeText(Long userId, String path, String content, Integer expectedRevision) {
        if (content == null) {
            throw new BizException("文件内容不能为空");
        }
        UserWorkspace workspace = workspace(userId);
        return WorkspaceAssembler.entry(write(workspace, userId, WorkspacePaths.file(path),
                content.getBytes(StandardCharsets.UTF_8), null, expectedRevision, WorkspaceActor.USER));
    }

    public WorkspaceEntryDTO upload(Long userId, String directory, String fileName, InputStream input, String contentType) {
        UserWorkspace workspace = workspace(userId);
        String path = WorkspacePaths.join(WorkspacePaths.directory(directory), WorkspacePaths.name(fileName));
        return WorkspaceAssembler.entry(write(workspace, userId, path, readLimited(input), contentType, null, WorkspaceActor.USER));
    }

    public WorkspaceEntryDTO createDirectory(Long userId, String path) {
        return WorkspaceAssembler.entry(createDirectory(workspace(userId), WorkspacePaths.directory(path), WorkspaceActor.USER));
    }

    public WorkspaceEntryDTO move(Long userId, String from, String to) {
        return WorkspaceAssembler.entry(move(workspace(userId), from, to, WorkspaceActor.USER));
    }

    public int delete(Long userId, String path) {
        return trash(workspace(userId), path);
    }

    public List<WorkspaceTrashDTO> trash(Long userId) {
        List<WorkspaceFile> trashed = files.listTrash(workspace(userId).getId());
        List<WorkspaceTrashDTO> roots = new ArrayList<>();
        for (WorkspaceFile item : trashed) {
            List<WorkspaceFile> group = sameDeletion(trashed, item);
            boolean nested = trashed.stream().anyMatch(other -> other != item && other.getDeletedAt().equals(item.getDeletedAt())
                    && other.directory() && item.getPath().startsWith(other.getPath() + "/"));
            if (!nested) {
                roots.add(new WorkspaceTrashDTO(String.valueOf(item.getId()), item.getPath(), item.getKind().name(),
                        group.stream().mapToLong(WorkspaceFile::getSize).sum(), group.size() - 1, item.getDeletedAt(),
                        item.getDeletedAt().plusDays(TRASH_DAYS)));
            }
        }
        return roots;
    }

    public WorkspaceEntryDTO restore(Long userId, String trashId) {
        UserWorkspace workspace = workspace(userId);
        WorkspaceFile item = trashed(workspace, trashId);
        List<WorkspaceFile> group = sameDeletion(files.listTrash(workspace.getId()), item);
        String original = item.getPath();
        String target = availablePath(workspace, original);
        ensureParents(workspace, target, WorkspaceActor.USER);
        WorkspaceFile restoredRoot = null;
        for (WorkspaceFile entry : group) {
            String path = target + entry.getPath().substring(original.length());
            entry.restoreAs(path);
            WorkspaceFile saved = files.save(entry);
            if (entry.getId().equals(item.getId())) {
                restoredRoot = saved;
            }
        }
        return WorkspaceAssembler.entry(restoredRoot == null ? item : restoredRoot);
    }

    public void purgeTrash(Long userId, String trashId) {
        UserWorkspace workspace = workspace(userId);
        WorkspaceFile item = trashed(workspace, trashId);
        purge(sameDeletion(files.listTrash(workspace.getId()), item));
    }

    public int emptyTrash(Long userId) {
        List<WorkspaceFile> trashed = files.listTrash(workspace(userId).getId());
        purge(trashed);
        return trashed.size();
    }

    public List<WorkspaceVersionDTO> versions(Long userId, String path) {
        WorkspaceFile file = requireFile(workspace(userId), WorkspacePaths.file(path));
        return versions.listByFile(file.getId()).stream()
                .map(version -> WorkspaceAssembler.version(version, file.getCurrentVersionId())).toList();
    }

    /** 恢复历史版本：以旧内容写入一个新版本，历史保持只增 */
    public WorkspaceEntryDTO restoreVersion(Long userId, String path, String versionId) {
        UserWorkspace workspace = workspace(userId);
        WorkspaceFile file = requireFile(workspace, WorkspacePaths.file(path));
        WorkspaceFileVersion version = versions.findById(parseId(versionId))
                .filter(item -> item.fileId().equals(file.getId()))
                .orElseThrow(() -> new BizException("历史版本不存在"));
        byte[] data = bytes(storage.get(version.objectKey()), version.size());
        return WorkspaceAssembler.entry(write(workspace, userId, file.getPath(), data, version.contentType(), null, WorkspaceActor.USER));
    }

    /** 把聊天附件（调用者自己上传的文件对象）另存到工作区目录，同名时自动改名 */
    public WorkspaceEntryDTO saveAttachment(Long userId, String fileId, String directory) {
        UserWorkspace workspace = workspace(userId);
        FileContentDTO content = fileAppService.content(parseId(fileId), userId, false);
        try (InputStream input = content.getInputStream()) {
            String name = StringUtils.hasText(content.getOriginalName()) ? content.getOriginalName().replace('/', '_').replace('\\', '_') : "附件";
            String path = availablePath(workspace, WorkspacePaths.join(WorkspacePaths.directory(directory), WorkspacePaths.name(name)));
            return WorkspaceAssembler.entry(write(workspace, userId, path, readLimited(input), content.getContentType(), null, WorkspaceActor.USER));
        } catch (IOException e) {
            throw new BizException("读取附件失败");
        }
    }

    public WorkspaceConfigDTO config() {
        capabilities.ensureEnabled("ai", "AI 助手");
        return new WorkspaceConfigDTO(quotaMb(), tmpRetentionDays());
    }

    public WorkspaceConfigDTO updateConfig(WorkspaceConfigCmd cmd) {
        capabilities.ensureEnabled("ai", "AI 助手");
        if (cmd.getQuotaMb() < 1 || cmd.getQuotaMb() > 10_000_000) {
            throw new BizException("工作区配额需在 1 MB 到 10 TB 之间");
        }
        if (cmd.getTmpRetentionDays() < 1 || cmd.getTmpRetentionDays() > 365) {
            throw new BizException("tmp/ 清理天数需在 1 到 365 之间");
        }
        CapabilityModule module = capabilityModules.findByCode("ai").orElseThrow(() -> new BizException("AI 助手能力配置不存在"));
        Map<String, String> config = new HashMap<>(module.getConfig() == null ? Map.of() : module.getConfig());
        config.put("workspaceQuotaMb", String.valueOf(cmd.getQuotaMb()));
        config.put("workspaceTmpDays", String.valueOf(cmd.getTmpRetentionDays()));
        module.updateConfig(config);
        capabilityModules.save(module);
        return config();
    }

    /** AI 工具的访问视图：路径相对 root（空串为整个工作区，受限应用为 apps/&lt;code&gt;），变更记为 AI 来源 */
    public WorkspaceAccess access(Long userId, String root) {
        return new WorkspaceAccess(this, userId, WorkspacePaths.directory(root));
    }

    /** 每日清理：回收站超过 30 天、tmp/ 超过配置天数的文件连同全部版本内容永久删除 */
    @Scheduled(cron = "0 23 3 * * *")
    public void purgeExpired() {
        if (!capabilities.enabled("ai")) {
            return;
        }
        long trashed = purgeLoop(() -> files.findTrashedBefore(LocalDateTime.now().minusDays(TRASH_DAYS), PURGE_BATCH));
        long temporary = purgeLoop(() -> files.findStaleFilesUnder(TMP_DIR, LocalDateTime.now().minusDays(tmpRetentionDays()), PURGE_BATCH));
        if (trashed + temporary > 0) {
            log.info("工作区清理：回收站 {} 项，tmp/ {} 个文件", trashed, temporary);
        }
    }

    // ---- WorkspaceAccess 与端点共用的核心操作 ----

    UserWorkspace workspace(Long userId) {
        capabilities.ensureEnabled("ai", "AI 助手");
        if (userId == null || userId <= 0) {
            throw new BizException("当前用户未登录");
        }
        return workspaces.getOrCreate(userId);
    }

    List<WorkspaceFile> listActive(UserWorkspace workspace) {
        return files.listActive(workspace.getId());
    }

    Optional<WorkspaceFile> find(UserWorkspace workspace, String path) {
        return files.findActive(workspace.getId(), path);
    }

    WorkspaceFileContentDTO preview(UserWorkspace workspace, String path, int maxChars) {
        WorkspaceFile file = requireFile(workspace, path);
        if (file.getSize() > PREVIEW_BYTES) {
            return new WorkspaceFileContentDTO(file.getPath(), "binary", null, false, file.getSize(), file.getContentType(), file.getRevision());
        }
        byte[] data = bytes(object(file), file.getSize());
        String text = text(data);
        if (text == null) {
            return new WorkspaceFileContentDTO(file.getPath(), "binary", null, false, file.getSize(), file.getContentType(), file.getRevision());
        }
        boolean truncated = text.length() > maxChars;
        return new WorkspaceFileContentDTO(file.getPath(), "text", truncated ? text.substring(0, maxChars) : text, truncated,
                file.getSize(), file.getContentType(), file.getRevision());
    }

    /** 整个文件内容（运行器输入用；超过单文件上限拒绝） */
    byte[] readBytes(UserWorkspace workspace, String path) {
        WorkspaceFile file = requireFile(workspace, path);
        return bytes(object(file), file.getSize());
    }

    WorkspaceFile write(UserWorkspace workspace, Long userId, String path, byte[] data, String contentType,
                        Integer expectedRevision, WorkspaceActor actor) {
        if (data.length > MAX_FILE_BYTES) {
            throw new BizException("单个文件不能超过 100 MB：" + path);
        }
        Optional<WorkspaceFile> existing = find(workspace, path);
        if (existing.isPresent() && existing.get().directory()) {
            throw new BizException("同名目录已存在：" + path);
        }
        if (expectedRevision != null && existing.map(WorkspaceFile::getRevision).orElse(0) != expectedRevision.intValue()) {
            throw new BizException("文件已被修改（可能是 AI 或其他窗口），请重新打开后再保存：" + path);
        }
        String sha256 = sha256(data);
        if (existing.isPresent() && sha256.equals(existing.get().getSha256())) {
            return existing.get();
        }
        ensureCapacity(workspace, userId, data.length);
        ensureParents(workspace, path, actor);
        boolean created = existing.isEmpty();
        WorkspaceFile file = existing.orElseGet(() -> files.save(WorkspaceFile.file(workspace.getId(), path)));
        Long versionId = versions.nextId();
        String objectKey = WorkspaceFileVersion.objectKey(workspace.getId(), versionId);
        String type = contentType(path, contentType);
        try {
            storage.put(objectKey, new ByteArrayInputStream(data), data.length, type);
        } catch (RuntimeException error) {
            if (created) {
                files.delete(file.getId());
            }
            throw error;
        }
        WorkspaceFileVersion version = versions.save(new WorkspaceFileVersion(versionId, workspace.getId(), file.getId(),
                file.getRevision() + 1, objectKey, data.length, sha256, type, actor, LocalDateTime.now()));
        file.recordVersion(version);
        WorkspaceFile saved = files.save(file);
        pruneVersions(saved);
        return saved;
    }

    WorkspaceFile createDirectory(UserWorkspace workspace, String path, WorkspaceActor actor) {
        if (path.isEmpty()) {
            throw new BizException("目录名不能为空");
        }
        Optional<WorkspaceFile> existing = find(workspace, path);
        if (existing.isPresent()) {
            if (existing.get().directory()) {
                return existing.get();
            }
            throw new BizException("同名文件已存在：" + path);
        }
        ensureParents(workspace, path, actor);
        return files.save(WorkspaceFile.directory(workspace.getId(), path, actor));
    }

    WorkspaceFile move(UserWorkspace workspace, String rawFrom, String rawTo, WorkspaceActor actor) {
        String from = WorkspacePaths.directory(rawFrom);
        WorkspaceFile source = find(workspace, from).orElseThrow(() -> new BizException("工作区中没有该文件或目录：" + from));
        String target = source.directory() ? WorkspacePaths.directory(rawTo) : WorkspacePaths.file(rawTo);
        if (target.isEmpty()) {
            throw new BizException("目标路径不能为空");
        }
        if (target.equals(from)) {
            return source;
        }
        if (source.directory() && WorkspacePaths.within(target, from)) {
            throw new BizException("不能把目录移动到它自己里面");
        }
        if (find(workspace, target).isPresent()) {
            throw new BizException("目标已存在：" + target);
        }
        ensureParents(workspace, target, actor);
        List<WorkspaceFile> affected = source.directory()
                ? listActive(workspace).stream().filter(entry -> entry.within(from)).toList()
                : List.of(source);
        WorkspaceFile moved = source;
        for (WorkspaceFile entry : affected) {
            entry.moveTo(target + entry.getPath().substring(from.length()), actor);
            WorkspaceFile saved = files.save(entry);
            if (entry.getId().equals(source.getId())) {
                moved = saved;
            }
        }
        return moved;
    }

    int trash(UserWorkspace workspace, String rawPath) {
        String path = WorkspacePaths.directory(rawPath);
        WorkspaceFile source = find(workspace, path).orElseThrow(() -> new BizException("工作区中没有该文件或目录：" + path));
        LocalDateTime now = LocalDateTime.now();
        List<WorkspaceFile> affected = source.directory()
                ? listActive(workspace).stream().filter(entry -> entry.within(path)).toList()
                : List.of(source);
        for (WorkspaceFile entry : affected) {
            entry.trash(now);
            files.save(entry);
        }
        return affected.size();
    }

    // ---- 内部规则 ----

    private void ensureParents(UserWorkspace workspace, String path, WorkspaceActor actor) {
        String parent = WorkspacePaths.parent(path);
        if (parent.isEmpty()) {
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String segment : parent.split("/")) {
            current.append(current.isEmpty() ? "" : "/").append(segment);
            String directory = current.toString();
            Optional<WorkspaceFile> existing = find(workspace, directory);
            if (existing.isPresent() && !existing.get().directory()) {
                throw new BizException("路径中的「" + directory + "」是文件，不能作为目录");
            }
            if (existing.isEmpty()) {
                files.save(WorkspaceFile.directory(workspace.getId(), directory, actor));
            }
        }
    }

    private void ensureCapacity(UserWorkspace workspace, Long userId, long incoming) {
        Long quota = quotaBytes(userId);
        if (quota == null) {
            return;
        }
        long used = versions.usedBytes(workspace.getId());
        if (used + incoming > quota) {
            throw new BizException("工作区空间不足：已用 " + megabytes(used) + " / 配额 " + megabytes(quota)
                    + "，可清空回收站或删除不需要的文件后重试");
        }
    }

    private void pruneVersions(WorkspaceFile file) {
        List<WorkspaceFileVersion> history = versions.listByFile(file.getId());
        int kept = 0;
        for (WorkspaceFileVersion version : history) {
            if (version.id().equals(file.getCurrentVersionId()) || kept < KEEP_VERSIONS - 1) {
                if (!version.id().equals(file.getCurrentVersionId())) {
                    kept++;
                }
                continue;
            }
            deleteVersion(version);
        }
    }

    private void purge(List<WorkspaceFile> entries) {
        for (WorkspaceFile entry : entries) {
            if (!entry.directory()) {
                versions.listByFile(entry.getId()).forEach(this::deleteVersion);
            }
            files.delete(entry.getId());
        }
    }

    private long purgeLoop(java.util.function.Supplier<List<WorkspaceFile>> batch) {
        long total = 0;
        for (int round = 0; round < 50; round++) {
            List<WorkspaceFile> entries = batch.get();
            if (entries.isEmpty()) {
                break;
            }
            purge(entries);
            total += entries.size();
        }
        return total;
    }

    private void deleteVersion(WorkspaceFileVersion version) {
        try {
            storage.delete(version.objectKey());
        } catch (RuntimeException error) {
            log.warn("删除工作区内容对象失败，保留版本记录待下次清理：{}", version.objectKey(), error);
            return;
        }
        versions.delete(version.id());
    }

    private List<WorkspaceFile> sameDeletion(List<WorkspaceFile> trashed, WorkspaceFile root) {
        return trashed.stream()
                .filter(entry -> entry.getDeletedAt().equals(root.getDeletedAt()) && entry.within(root.getPath()))
                .toList();
    }

    private WorkspaceFile trashed(UserWorkspace workspace, String trashId) {
        return files.findById(parseId(trashId))
                .filter(entry -> entry.getWorkspaceId().equals(workspace.getId()) && entry.trashed())
                .orElseThrow(() -> new BizException("回收站中没有该条目"));
    }

    /** 路径被占用时追加「 (2)」「 (3)」…（插在扩展名前） */
    private String availablePath(UserWorkspace workspace, String path) {
        if (find(workspace, path).isEmpty()) {
            return path;
        }
        String name = WorkspacePaths.fileName(path);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? path.substring(0, path.length() - (name.length() - dot)) : path;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int index = 2; index < 1000; index++) {
            String candidate = base + " (" + index + ")" + extension;
            if (find(workspace, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BizException("同名文件过多：" + path);
    }

    private WorkspaceFile requireFile(UserWorkspace workspace, String path) {
        WorkspaceFile file = find(workspace, path).orElseThrow(() -> new BizException("工作区中没有该文件：" + path));
        if (file.directory()) {
            throw new BizException("这是目录，不是文件：" + path);
        }
        return file;
    }

    private StoredObject object(WorkspaceFile file) {
        WorkspaceFileVersion version = versions.findById(file.getCurrentVersionId())
                .orElseThrow(() -> new BizException("文件内容缺失：" + file.getPath()));
        return storage.get(version.objectKey());
    }

    private static byte[] bytes(StoredObject object, long expected) {
        try (InputStream input = object.inputStream()) {
            return input.readNBytes((int) Math.min(Math.max(expected, 0), MAX_FILE_BYTES));
        } catch (IOException e) {
            throw new BizException("读取文件内容失败");
        }
    }

    private static byte[] readLimited(InputStream input) {
        try (input) {
            byte[] data = input.readNBytes((int) MAX_FILE_BYTES + 1);
            if (data.length > MAX_FILE_BYTES) {
                throw new BizException("单个文件不能超过 100 MB");
            }
            return data;
        } catch (IOException e) {
            throw new BizException("读取上传文件失败");
        }
    }

    /** UTF-8 文本返回正文；含 NUL 或不是合法 UTF-8 时视为二进制返回 null */
    static String text(byte[] data) {
        for (int i = 0; i < Math.min(data.length, 8192); i++) {
            if (data[i] == 0) {
                return null;
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    static String contentType(String path, String declared) {
        String name = WorkspacePaths.fileName(path);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (TEXT_EXTENSIONS.contains(extension)) {
            return "md".equals(extension) || "markdown".equals(extension) ? "text/markdown" : "text/plain";
        }
        if (StringUtils.hasText(declared) && !declared.startsWith("application/octet-stream")) {
            return declared.split(";")[0].trim();
        }
        String guessed = URLConnection.guessContentTypeFromName(name);
        return guessed == null ? "application/octet-stream" : guessed;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Long quotaBytes(Long userId) {
        return quotas.unlimited(userId) ? null : quotaMb() * 1024 * 1024;
    }

    private long quotaMb() {
        return configured("workspaceQuotaMb", DEFAULT_QUOTA_MB);
    }

    private int tmpRetentionDays() {
        return (int) configured("workspaceTmpDays", DEFAULT_TMP_DAYS);
    }

    private long configured(String key, long fallback) {
        try {
            String value = capabilityModules.findByCode("ai").map(CapabilityModule::getConfig)
                    .map(config -> config.get(key)).orElse(null);
            long parsed = value == null ? fallback : Long.parseLong(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException e) {
            throw new BizException("标识无效：" + value);
        }
    }
}
