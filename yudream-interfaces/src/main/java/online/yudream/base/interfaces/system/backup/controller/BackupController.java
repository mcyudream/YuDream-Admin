package online.yudream.base.interfaces.system.backup.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.backup.cmd.BackupPlanCmd;
import online.yudream.base.application.system.backup.cmd.RemoteTargetCmd;
import online.yudream.base.application.system.backup.dto.BackupAnalysisDTO;
import online.yudream.base.application.system.backup.dto.BackupArchiveDownloadDTO;
import online.yudream.base.application.system.backup.dto.BackupJobDTO;
import online.yudream.base.application.system.backup.dto.BackupPlanDTO;
import online.yudream.base.application.system.backup.dto.BackupScopeDTO;
import online.yudream.base.application.system.backup.dto.RemoteTargetDTO;
import online.yudream.base.application.system.backup.service.BackupArchiveAppService;
import online.yudream.base.application.system.backup.service.RemoteBackupAppService;
import online.yudream.base.application.system.backup.support.BackupChunkUploadManager;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.backup.assembler.BackupWebAssembler;
import online.yudream.base.interfaces.system.backup.request.BackupExportRequest;
import online.yudream.base.interfaces.system.backup.request.BackupImportStagedRequest;
import online.yudream.base.interfaces.system.backup.request.BackupPlanRequest;
import online.yudream.base.interfaces.system.backup.request.BackupRestoreRequest;
import online.yudream.base.interfaces.system.backup.request.BackupUploadAbortRequest;
import online.yudream.base.interfaces.system.backup.request.BackupUploadBeginRequest;
import online.yudream.base.interfaces.system.backup.request.BackupUploadFinishRequest;
import online.yudream.base.interfaces.system.backup.request.RemoteTargetRequest;
import online.yudream.base.interfaces.system.backup.res.BackupAnalysisRes;
import online.yudream.base.interfaces.system.backup.res.BackupJobRes;
import online.yudream.base.interfaces.system.backup.res.BackupPlanRes;
import online.yudream.base.interfaces.system.backup.res.BackupScopeRes;
import online.yudream.base.interfaces.system.backup.res.RemoteArchiveRes;
import online.yudream.base.interfaces.system.backup.res.RemoteTargetRes;
import org.springframework.core.io.FileSystemResource;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据备份中心：全量导出/合并导入、异地目标与计划、任务历史与异地恢复。
 */
@RestController
@RequestMapping("/api/system/backup")
@RequiredArgsConstructor
public class BackupController {

    private static final String MODULE = "数据备份";

    private final BackupArchiveAppService archiveService;
    private final RemoteBackupAppService remoteService;

    // ---------------------------------------------------------------- 范围与导出

    @GetMapping("/scopes")
    @PermissionRegister(code = "system:backup:view", name = "查看备份中心", module = MODULE, desc = "查看备份范围、任务与配置")
    public Result<List<BackupScopeRes>> scopes() {
        return Result.ok(BackupWebAssembler.toScopeResList(archiveService.scopes()));
    }

    @PostMapping("/export")
    @PermissionRegister(code = "system:backup:export", name = "创建全量导出", module = MODULE, desc = "创建系统/插件范围的全量备份归档任务")
    public Result<BackupJobRes> export(@RequestBody BackupExportRequest request) {
        BackupJobDTO job = archiveService.createExportJob(request.getScopeTags());
        return Result.ok(BackupWebAssembler.toRes(job));
    }

    @PostMapping("/import/analyze")
    @PermissionRegister(code = "system:backup:import", name = "分析备份归档", module = MODULE, desc = "解析上传归档并统计与本地数据的重复/缺失情况")
    public Result<BackupAnalysisRes> analyzeImport(@RequestParam("file") MultipartFile file) {
        requireArchive(file);
        try {
            BackupAnalysisDTO analysis = archiveService.analyze(
                    file.getOriginalFilename(), file.getInputStream(), file.getSize());
            return Result.ok(BackupWebAssembler.toRes(analysis));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("读取上传归档失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 分片导入（超大归档）

    @PostMapping("/import/upload/begin")
    @PermissionRegister(code = "system:backup:import", name = "分析备份归档", module = MODULE, desc = "开启分片上传会话")
    public Result<String> beginUpload(@RequestBody BackupUploadBeginRequest request) {
        if (request.getSize() == null || request.getSize() < 0) {
            throw new BizException("归档大小无效");
        }
        return Result.ok(archiveService.beginChunkUpload(request.getName(), request.getSize()));
    }

    @PostMapping("/import/upload/chunk")
    @PermissionRegister(code = "system:backup:import", name = "分析备份归档", module = MODULE, desc = "追加分片（偏移顺序）")
    public Result<Long> uploadChunk(@RequestParam("uploadId") String uploadId,
                                    @RequestParam("offset") long offset,
                                    jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        if (request.getContentLengthLong() > BackupChunkUploadManager.MAX_CHUNK_BYTES) {
            throw new BizException("单分片超过大小限制（16MB）");
        }
        return Result.ok(archiveService.writeChunk(uploadId, offset, request.getInputStream()));
    }

    @PostMapping("/import/upload/finish")
    @PermissionRegister(code = "system:backup:import", name = "分析备份归档", module = MODULE, desc = "结束分片上传并校验完整性")
    public Result<Void> finishUpload(@RequestBody BackupUploadFinishRequest request) {
        if (request.getUploadId() == null || request.getSize() == null || request.getSize() < 0) {
            throw new BizException("分片上传参数无效");
        }
        archiveService.finishChunkUpload(request.getUploadId(), request.getSize(), request.getSha256());
        return Result.ok();
    }

    @PostMapping("/import/upload/abort")
    @PermissionRegister(code = "system:backup:import", name = "分析备份归档", module = MODULE, desc = "中止分片上传并清理暂存")
    public Result<Void> abortUpload(@RequestBody BackupUploadAbortRequest request) {
        archiveService.abortChunkUpload(request.getUploadId());
        return Result.ok();
    }

    @PostMapping("/import/analyze/staged")
    @PermissionRegister(code = "system:backup:import", name = "分析备份归档", module = MODULE, desc = "分析已暂存的分片归档")
    public Result<BackupAnalysisRes> analyzeStaged(@RequestParam("uploadId") String uploadId) {
        return Result.ok(BackupWebAssembler.toRes(archiveService.analyzeStaged(uploadId)));
    }

    @PostMapping("/import/staged")
    @PermissionRegister(code = "system:backup:import", name = "合并导入备份", module = MODULE, desc = "用已暂存的分片归档按策略合并导入")
    public Result<BackupJobRes> importStaged(@RequestBody BackupImportStagedRequest request) {
        BackupConflictStrategy resolved = BackupWebAssembler.parseStrategy(request.getStrategy());
        BackupJobDTO job = archiveService.createImportJobFromStaged(request.getUploadId(), resolved);
        return Result.ok(BackupWebAssembler.toRes(job));
    }

    @PostMapping("/import")
    @PermissionRegister(code = "system:backup:import", name = "合并导入备份", module = MODULE, desc = "按所选合并策略把上传归档合并进本地数据")
    public Result<BackupJobRes> importArchive(@RequestParam("file") MultipartFile file,
                                              @RequestParam("strategy") String strategy) {
        requireArchive(file);
        BackupConflictStrategy resolved = BackupWebAssembler.parseStrategy(strategy);
        try {
            BackupJobDTO job = archiveService.createImportJob(
                    file.getOriginalFilename(), file.getInputStream(), file.getSize(), resolved);
            return Result.ok(BackupWebAssembler.toRes(job));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("读取上传归档失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 任务

    @GetMapping("/jobs")
    @PermissionRegister(code = "system:backup:view", name = "查看备份中心", module = MODULE, desc = "查看备份任务历史")
    public Result<List<BackupJobRes>> jobs(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return Result.ok(BackupWebAssembler.toJobResList(archiveService.jobs(limit)));
    }

    @DeleteMapping("/jobs/{id}")
    @PermissionRegister(code = "system:backup:delete", name = "删除备份任务", module = MODULE, desc = "删除任务记录并清理本机归档")
    public Result<Void> deleteJob(@PathVariable Long id) {
        archiveService.deleteJob(id);
        return Result.ok();
    }

    @GetMapping("/jobs/{id}/archive")
    @PermissionRegister(code = "system:backup:download", name = "下载备份归档", module = MODULE, desc = "下载导出任务生成的本机归档")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        BackupArchiveDownloadDTO archive = archiveService.archiveDownload(id);
        String encoded = URLEncoder.encode(archive.archiveName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(archive.archiveSize())
                .body(new FileSystemResource(archive.archivePath()));
    }

    // ---------------------------------------------------------------- 异地目标

    @GetMapping("/remote-targets")
    @PermissionRegister(code = "system:backup:view", name = "查看备份中心", module = MODULE, desc = "查看异地备份目标")
    public Result<List<RemoteTargetRes>> targets() {
        return Result.ok(BackupWebAssembler.toTargetResList(remoteService.targets()));
    }

    @PostMapping("/remote-targets")
    @PermissionRegister(code = "system:backup:config", name = "配置异地备份", module = MODULE, desc = "新增异地备份目标")
    public Result<RemoteTargetRes> createTarget(@RequestBody RemoteTargetRequest request) {
        RemoteTargetCmd cmd = BackupWebAssembler.toCmd(request);
        return Result.ok(BackupWebAssembler.toRes(remoteService.createTarget(cmd)));
    }

    @PutMapping("/remote-targets/{id}")
    @PermissionRegister(code = "system:backup:config", name = "配置异地备份", module = MODULE, desc = "修改异地备份目标")
    public Result<RemoteTargetRes> updateTarget(@PathVariable Long id, @RequestBody RemoteTargetRequest request) {
        RemoteTargetCmd cmd = BackupWebAssembler.toCmd(request);
        return Result.ok(BackupWebAssembler.toRes(remoteService.updateTarget(id, cmd)));
    }

    @DeleteMapping("/remote-targets/{id}")
    @PermissionRegister(code = "system:backup:delete", name = "删除备份配置", module = MODULE, desc = "删除异地备份目标")
    public Result<Void> deleteTarget(@PathVariable Long id) {
        remoteService.deleteTarget(id);
        return Result.ok();
    }

    @PostMapping("/remote-targets/{id}/enable")
    @PermissionRegister(code = "system:backup:config", name = "配置异地备份", module = MODULE, desc = "启用异地备份目标")
    public Result<Void> enableTarget(@PathVariable Long id) {
        remoteService.enableTarget(id);
        return Result.ok();
    }

    @PostMapping("/remote-targets/{id}/disable")
    @PermissionRegister(code = "system:backup:config", name = "配置异地备份", module = MODULE, desc = "停用异地备份目标")
    public Result<Void> disableTarget(@PathVariable Long id) {
        remoteService.disableTarget(id);
        return Result.ok();
    }

    @PostMapping("/remote-targets/{id}/test")
    @PermissionRegister(code = "system:backup:test", name = "测试异地连接", module = MODULE, desc = "测试异地备份目标连通性")
    public Result<Void> testTarget(@PathVariable Long id) {
        remoteService.testTarget(id);
        return Result.ok();
    }

    @GetMapping("/remote-targets/{id}/archives")
    @PermissionRegister(code = "system:backup:view", name = "查看备份中心", module = MODULE, desc = "查看远端备份归档列表")
    public Result<List<RemoteArchiveRes>> remoteArchives(@PathVariable Long id) {
        return Result.ok(BackupWebAssembler.toArchiveResList(remoteService.remoteArchives(id)));
    }

    @PostMapping("/remote-targets/{id}/restore")
    @PermissionRegister(code = "system:backup:run", name = "执行备份恢复", module = MODULE, desc = "从远端归档下载并合并恢复")
    public Result<Void> restore(@PathVariable Long id, @RequestBody BackupRestoreRequest request) {
        remoteService.createRestoreJob(id, request.getArchiveName(),
                BackupWebAssembler.parseStrategy(request.getStrategy()));
        return Result.ok();
    }

    // ---------------------------------------------------------------- 备份计划

    @GetMapping("/plans")
    @PermissionRegister(code = "system:backup:view", name = "查看备份中心", module = MODULE, desc = "查看备份计划")
    public Result<List<BackupPlanRes>> plans() {
        return Result.ok(BackupWebAssembler.toPlanResList(remoteService.plans()));
    }

    @PostMapping("/plans")
    @PermissionRegister(code = "system:backup:config", name = "配置异地备份", module = MODULE, desc = "新增定时备份计划")
    public Result<BackupPlanRes> createPlan(@RequestBody BackupPlanRequest request) {
        BackupPlanCmd cmd = BackupWebAssembler.toCmd(request);
        return Result.ok(BackupWebAssembler.toRes(remoteService.createPlan(cmd)));
    }

    @PutMapping("/plans/{id}")
    @PermissionRegister(code = "system:backup:config", name = "配置异地备份", module = MODULE, desc = "修改备份计划")
    public Result<BackupPlanRes> updatePlan(@PathVariable Long id, @RequestBody BackupPlanRequest request) {
        BackupPlanCmd cmd = BackupWebAssembler.toCmd(request);
        return Result.ok(BackupWebAssembler.toRes(remoteService.updatePlan(id, cmd)));
    }

    @DeleteMapping("/plans/{id}")
    @PermissionRegister(code = "system:backup:delete", name = "删除备份配置", module = MODULE, desc = "删除备份计划")
    public Result<Void> deletePlan(@PathVariable Long id) {
        remoteService.deletePlan(id);
        return Result.ok();
    }

    @PostMapping("/plans/{id}/enable")
    @PermissionRegister(code = "system:backup:config", name = "配置异地备份", module = MODULE, desc = "启用备份计划")
    public Result<Void> enablePlan(@PathVariable Long id) {
        remoteService.enablePlan(id);
        return Result.ok();
    }

    @PostMapping("/plans/{id}/disable")
    @PermissionRegister(code = "system:backup:config", name = "配置异地备份", module = MODULE, desc = "停用备份计划")
    public Result<Void> disablePlan(@PathVariable Long id) {
        remoteService.disablePlan(id);
        return Result.ok();
    }

    @PostMapping("/plans/{id}/run")
    @PermissionRegister(code = "system:backup:run", name = "执行异地备份", module = MODULE, desc = "手动触发一次计划备份并推送异地")
    public Result<Void> runPlan(@PathVariable Long id) {
        remoteService.runPlan(id);
        return Result.ok();
    }

    private static void requireArchive(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的备份归档文件");
        }
    }
}
