package online.yudream.base.interfaces.platform.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO;
import online.yudream.base.application.platform.chat.service.ChatAttachmentAppService;
import online.yudream.base.application.platform.chat.service.ChatQuotaAppService;
import online.yudream.base.application.platform.chat.service.ChatSessionAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.chat.assembler.ChatWebAssembler;
import online.yudream.base.interfaces.platform.chat.request.ChatQuotaConfigRequest;
import online.yudream.base.interfaces.platform.chat.request.ChatSendRequest;
import online.yudream.base.interfaces.platform.chat.request.ChatSessionSaveRequest;
import online.yudream.base.interfaces.platform.chat.res.ChatAttachmentRes;
import online.yudream.base.interfaces.platform.chat.res.ChatMessageRes;
import online.yudream.base.interfaces.platform.chat.res.ChatQuotaConfigRes;
import online.yudream.base.interfaces.platform.chat.res.ChatQuotaRes;
import online.yudream.base.interfaces.platform.chat.res.ChatSessionRes;
import online.yudream.base.interfaces.platform.chat.support.ChatStreamSupport;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/platform/chat")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class ChatController {

    private final ChatSessionAppService sessionService;
    private final ChatQuotaAppService quotaService;
    private final ChatAttachmentAppService attachmentService;
    private final ChatStreamSupport streamSupport;

    @GetMapping("/sessions")
    @PermissionRegister(code = "platform:chat:session:view", name = "查看 AI 助手会话", module = "平台能力", desc = "查看当前用户的 AI 助手会话列表")
    public Result<List<ChatSessionRes>> sessions() {
        Long userId = SecurityPrincipalSupport.current().userId();
        return Result.ok(sessionService.list(userId).stream().map(ChatWebAssembler::session).toList());
    }

    @PostMapping("/sessions")
    @PermissionRegister(code = "platform:chat:session:edit", name = "创建 AI 助手会话", module = "平台能力", desc = "创建新的 AI 助手会话")
    public Result<ChatSessionRes> createSession(@Valid @RequestBody ChatSessionSaveRequest request) {
        Long userId = SecurityPrincipalSupport.current().userId();
        return Result.ok(ChatWebAssembler.session(sessionService.create(userId, ChatWebAssembler.session(null, request))));
    }

    @PatchMapping("/sessions/{id}")
    @PermissionRegister(code = "platform:chat:session:edit", name = "更新 AI 助手会话", module = "平台能力", desc = "重命名、切换上下文或固定 AI 助手会话")
    public Result<ChatSessionRes> updateSession(@PathVariable Long id, @Valid @RequestBody ChatSessionSaveRequest request) {
        Long userId = SecurityPrincipalSupport.current().userId();
        return Result.ok(ChatWebAssembler.session(sessionService.update(userId, ChatWebAssembler.session(id, request))));
    }

    @DeleteMapping("/sessions/{id}")
    @PermissionRegister(code = "platform:chat:session:edit", name = "删除 AI 助手会话", module = "平台能力", desc = "删除当前用户的 AI 助手会话")
    public Result<Void> deleteSession(@PathVariable Long id) {
        Long userId = SecurityPrincipalSupport.current().userId();
        sessionService.delete(userId, id);
        return Result.ok();
    }

    @GetMapping("/sessions/{id}/messages")
    @PermissionRegister(code = "platform:chat:session:view", name = "查看 AI 助手消息", module = "平台能力", desc = "查看 AI 助手会话历史消息")
    public Result<List<ChatMessageRes>> messages(@PathVariable Long id) {
        Long userId = SecurityPrincipalSupport.current().userId();
        return Result.ok(sessionService.messages(userId, id).stream().map(ChatWebAssembler::message).toList());
    }

    @PostMapping(value = "/sessions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:chat:use", name = "AI 助手流式问答", module = "平台能力", desc = "在指定会话中进行 AG-UI 流式问答")
    public SseEmitter stream(@PathVariable Long id, @Valid @RequestBody ChatSendRequest request) {
        var principal = SecurityPrincipalSupport.current();
        var cmd = ChatWebAssembler.send(request, principal);
        cmd.setSessionId(id);
        return streamSupport.stream(principal.userId(), cmd);
    }

    @PostMapping(value = "/stream-once", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:chat:use", name = "AI 助手临时问答", module = "平台能力", desc = "不创建会话直接进行 AG-UI 流式问答")
    public SseEmitter streamOnce(@Valid @RequestBody ChatSendRequest request) {
        var principal = SecurityPrincipalSupport.current();
        return streamSupport.streamOnce(principal.userId(), ChatWebAssembler.send(request, principal));
    }

    @PostMapping("/attachments")
    @PermissionRegister(code = "platform:chat:use", name = "上传 AI 助手附件", module = "平台能力", desc = "上传图片或文档作为 AI 助手附件")
    public Result<ChatAttachmentRes> upload(@RequestPart("file") MultipartFile file) {
        Long userId = SecurityPrincipalSupport.current().userId();
        ChatAttachmentDTO dto;
        try {
            dto = attachmentService.upload(file.getInputStream(), file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), userId);
        }
        catch (online.yudream.base.domain.common.exception.BizException error) {
            throw error;
        }
        catch (Exception error) {
            throw new online.yudream.base.domain.common.exception.BizException(
                    "附件上传失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage()));
        }
        return Result.ok(ChatWebAssembler.attachment(dto));
    }

    @GetMapping("/quota/me")
    @PermissionRegister(code = "platform:chat:use", name = "查看 AI 助手额度", module = "平台能力", desc = "查看当前用户的每日 token 额度使用情况")
    public Result<ChatQuotaRes> myQuota() {
        Long userId = SecurityPrincipalSupport.current().userId();
        return Result.ok(ChatWebAssembler.quota(quotaService.me(userId)));
    }

    @GetMapping("/quota/config")
    @PermissionRegister(code = "platform:chat:quota:config", name = "查看 AI 助手额度配置", module = "平台能力", desc = "查看 AI 助手每日 token 上限配置")
    public Result<ChatQuotaConfigRes> quotaConfig() {
        return Result.ok(ChatWebAssembler.quotaConfig(quotaService.dailyLimit()));
    }

    @PutMapping("/quota/config")
    @PermissionRegister(code = "platform:chat:quota:config", name = "配置 AI 助手额度", module = "平台能力", desc = "配置 AI 助手每日 token 上限")
    public Result<ChatQuotaConfigRes> updateQuotaConfig(@Valid @RequestBody ChatQuotaConfigRequest request) {
        long dailyTokenLimit = quotaService.updateLimit(ChatWebAssembler.quotaConfig(request));
        return Result.ok(ChatWebAssembler.quotaConfig(dailyTokenLimit));
    }
}
