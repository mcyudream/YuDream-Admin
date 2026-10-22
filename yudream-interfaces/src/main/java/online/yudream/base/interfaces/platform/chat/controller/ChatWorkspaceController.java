package online.yudream.base.interfaces.platform.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.workspace.dto.WorkspaceDownloadDTO;
import online.yudream.base.application.platform.workspace.service.WorkspaceAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.chat.assembler.WorkspaceWebAssembler;
import online.yudream.base.interfaces.platform.chat.request.WorkspaceAttachmentSaveRequest;
import online.yudream.base.interfaces.platform.chat.request.WorkspaceConfigRequest;
import online.yudream.base.interfaces.platform.chat.request.WorkspaceMoveRequest;
import online.yudream.base.interfaces.platform.chat.request.WorkspacePathRequest;
import online.yudream.base.interfaces.platform.chat.request.WorkspaceTextSaveRequest;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceConfigRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceEntryRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceFileContentRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceOverviewRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceTrashRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceVersionRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 用户工作区：每用户一个，元数据在数据库、内容在对象存储；支持目录、预览/编辑、上传、移动改名、
 * 回收站与历史版本。查看需 platform:chat:workspace:view，修改需 platform:chat:workspace:edit。
 */
@RestController
@RequestMapping("/api/platform/chat/workspace")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class ChatWorkspaceController {

    private static final String VIEW = "platform:chat:workspace:view";
    private static final String EDIT = "platform:chat:workspace:edit";

    private final WorkspaceAppService workspaces;
    private final online.yudream.base.application.platform.workspace.service.WorkspaceCodeRunAppService codeRunner;

    @GetMapping
    @PermissionRegister(code = VIEW, name = "查看我的工作区", module = "AI 助手", desc = "查看当前用户工作区目录、文件与用量")
    public Result<WorkspaceOverviewRes> overview() {
        return Result.ok(WorkspaceWebAssembler.overview(workspaces.overview(currentUserId())));
    }

    @GetMapping("/file")
    @PermissionRegister(code = VIEW, name = "读取我的工作区文件", module = "AI 助手", desc = "预览当前用户工作区文件内容")
    public Result<WorkspaceFileContentRes> read(@RequestParam String path) {
        return Result.ok(WorkspaceWebAssembler.content(workspaces.read(currentUserId(), path)));
    }

    /** 下载原始内容：一律按附件下发并禁止嗅探，界面预览图片时自行用元数据类型构造 Blob */
    @GetMapping("/file/download")
    @PermissionRegister(code = VIEW, name = "下载我的工作区文件", module = "AI 助手", desc = "下载当前用户工作区文件")
    public ResponseEntity<InputStreamResource> download(@RequestParam String path) {
        WorkspaceDownloadDTO file = workspaces.download(currentUserId(), path);
        String encodedName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.size())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new InputStreamResource(file.inputStream()));
    }

    @PutMapping("/file")
    @PermissionRegister(code = EDIT, name = "编辑我的工作区文件", module = "AI 助手", desc = "新建或保存当前用户工作区文本文件")
    public Result<WorkspaceEntryRes> save(@Valid @RequestBody WorkspaceTextSaveRequest request) {
        return Result.ok(WorkspaceWebAssembler.entry(workspaces.writeText(currentUserId(), request.getPath(), request.getContent(),
                request.getExpectedRevision())));
    }

    @PostMapping("/upload")
    @PermissionRegister(code = EDIT, name = "上传我的工作区文件", module = "AI 助手", desc = "上传文件到当前用户工作区目录")
    public Result<WorkspaceEntryRes> upload(@RequestParam(required = false) String directory,
                                            @RequestPart("file") MultipartFile file) throws IOException {
        return Result.ok(WorkspaceWebAssembler.entry(workspaces.upload(currentUserId(), directory, file.getOriginalFilename(),
                file.getInputStream(), file.getContentType())));
    }

    @PostMapping("/directory")
    @PermissionRegister(code = EDIT, name = "新建我的工作区目录", module = "AI 助手", desc = "在当前用户工作区新建目录")
    public Result<WorkspaceEntryRes> createDirectory(@Valid @RequestBody WorkspacePathRequest request) {
        return Result.ok(WorkspaceWebAssembler.entry(workspaces.createDirectory(currentUserId(), request.getPath())));
    }

    @PostMapping("/move")
    @PermissionRegister(code = EDIT, name = "移动我的工作区文件", module = "AI 助手", desc = "移动或重命名当前用户工作区文件与目录")
    public Result<WorkspaceEntryRes> move(@Valid @RequestBody WorkspaceMoveRequest request) {
        return Result.ok(WorkspaceWebAssembler.entry(workspaces.move(currentUserId(), request.getFrom(), request.getTo())));
    }

    @DeleteMapping("/file")
    @PermissionRegister(code = EDIT, name = "删除我的工作区文件", module = "AI 助手", desc = "把当前用户工作区文件或目录移入回收站")
    public Result<Integer> delete(@RequestParam String path) {
        return Result.ok(workspaces.delete(currentUserId(), path));
    }

    @GetMapping("/trash")
    @PermissionRegister(code = VIEW, name = "查看我的工作区回收站", module = "AI 助手", desc = "查看当前用户工作区回收站")
    public Result<List<WorkspaceTrashRes>> trash() {
        return Result.ok(WorkspaceWebAssembler.trash(workspaces.trash(currentUserId())));
    }

    @PostMapping("/trash/{id}/restore")
    @PermissionRegister(code = EDIT, name = "恢复我的工作区文件", module = "AI 助手", desc = "从回收站恢复当前用户工作区文件")
    public Result<WorkspaceEntryRes> restore(@PathVariable String id) {
        return Result.ok(WorkspaceWebAssembler.entry(workspaces.restore(currentUserId(), id)));
    }

    @DeleteMapping("/trash/{id}")
    @PermissionRegister(code = EDIT, name = "彻底删除我的工作区文件", module = "AI 助手", desc = "从回收站永久删除当前用户工作区文件")
    public Result<Void> purge(@PathVariable String id) {
        workspaces.purgeTrash(currentUserId(), id);
        return Result.ok(null);
    }

    @DeleteMapping("/trash")
    @PermissionRegister(code = EDIT, name = "清空我的工作区回收站", module = "AI 助手", desc = "永久删除当前用户工作区回收站全部内容")
    public Result<Integer> emptyTrash() {
        return Result.ok(workspaces.emptyTrash(currentUserId()));
    }

    @GetMapping("/versions")
    @PermissionRegister(code = VIEW, name = "查看我的工作区文件历史", module = "AI 助手", desc = "查看当前用户工作区文件的历史版本")
    public Result<List<WorkspaceVersionRes>> versions(@RequestParam String path) {
        return Result.ok(WorkspaceWebAssembler.versions(workspaces.versions(currentUserId(), path)));
    }

    @PostMapping("/versions/{id}/restore")
    @PermissionRegister(code = EDIT, name = "恢复我的工作区文件版本", module = "AI 助手", desc = "把当前用户工作区文件恢复为历史版本")
    public Result<WorkspaceEntryRes> restoreVersion(@PathVariable String id, @Valid @RequestBody WorkspacePathRequest request) {
        return Result.ok(WorkspaceWebAssembler.entry(workspaces.restoreVersion(currentUserId(), request.getPath(), id)));
    }

    @PostMapping("/attachments")
    @PermissionRegister(code = EDIT, name = "附件保存到我的工作区", module = "AI 助手", desc = "把自己上传的聊天附件保存到工作区目录")
    public Result<WorkspaceEntryRes> saveAttachment(@Valid @RequestBody WorkspaceAttachmentSaveRequest request) {
        return Result.ok(WorkspaceWebAssembler.entry(workspaces.saveAttachment(currentUserId(), request.getFileId(), request.getDirectory())));
    }

    /** 代码运行器状态：是否可在编码模式中让助手运行工作区代码 */
    @GetMapping("/runner")
    @PermissionRegister(code = "platform:chat:workspace:run", name = "运行我的工作区代码", module = "AI 助手", desc = "允许助手在用户确认后于隔离运行器中执行工作区代码")
    public Result<online.yudream.base.interfaces.platform.chat.res.WorkspaceRunnerRes> runner() {
        return Result.ok(WorkspaceWebAssembler.runner(codeRunner.unavailableReason(), codeRunner.runtimes()));
    }

    @GetMapping("/config")
    @PermissionRegister(code = "platform:chat:quota:config", name = "配置 AI 助手额度", module = "平台能力", desc = "配置 AI 助手每日 token 上限与工作区配额")
    public Result<WorkspaceConfigRes> config() {
        return Result.ok(WorkspaceWebAssembler.config(workspaces.config()));
    }

    @PutMapping("/config")
    @PermissionRegister(code = "platform:chat:quota:config", name = "配置 AI 助手额度", module = "平台能力", desc = "配置 AI 助手每日 token 上限与工作区配额")
    public Result<WorkspaceConfigRes> updateConfig(@Valid @RequestBody WorkspaceConfigRequest request) {
        return Result.ok(WorkspaceWebAssembler.config(workspaces.updateConfig(WorkspaceWebAssembler.config(request))));
    }

    private static Long currentUserId() {
        return SecurityPrincipalSupport.current().userId();
    }
}
