package online.yudream.base.interfaces.platform.satori.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.satori.service.SatoriConnectionAppService;
import online.yudream.base.application.platform.satori.service.MessageDeliveryAppService;
import online.yudream.base.application.platform.satori.service.SatoriConversationAppService;
import online.yudream.base.application.platform.satori.service.SatoriOperationLogAppService;
import online.yudream.base.application.platform.satori.service.SatoriEventAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.satori.assembler.SatoriConnectionWebAssembler;
import online.yudream.base.interfaces.platform.satori.assembler.SatoriMessageWebAssembler;
import online.yudream.base.interfaces.platform.satori.assembler.SatoriConversationWebAssembler;
import online.yudream.base.interfaces.platform.satori.assembler.SatoriMediaSendWebAssembler;
import online.yudream.base.interfaces.platform.satori.request.SatoriInternalInvokeRequest;
import online.yudream.base.interfaces.platform.satori.request.SatoriMessageSendRequest;
import online.yudream.base.interfaces.platform.satori.request.SatoriConnectionCreateRequest;
import online.yudream.base.interfaces.platform.satori.request.SatoriConnectionUpdateRequest;
import online.yudream.base.interfaces.platform.satori.res.SatoriConnectionRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriConnectionTestRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriMessageSendRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriOperationLogRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriEventDetailRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriConversationPageRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriConversationRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriChatMessagePageRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriChatMemberRes;
import online.yudream.base.interfaces.platform.satori.service.SatoriLiveLogHub;
import online.yudream.base.interfaces.platform.satori.service.SatoriLiveMessageHub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/platform/satori/connections")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.satori", name = "enabled", havingValue = "true")
public class SatoriConnectionController {
    private final SatoriConnectionAppService connectionAppService;
    private final MessageDeliveryAppService messageDeliveryAppService;
    private final SatoriOperationLogAppService operationLogAppService;
    private final SatoriLiveLogHub liveLogHub;
    private final SatoriEventAppService eventAppService;
    private final SatoriConversationAppService conversationAppService;
    private final SatoriLiveMessageHub liveMessageHub;

    @GetMapping
    @PermissionRegister(code = "platform:satori:view", name = "查看 Satori 连接", module = "Satori 平台", desc = "查看 Satori 连接列表")
    public Result<PageResult<SatoriConnectionRes>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.page(SatoriConnectionWebAssembler.toQuery(keyword, page, size))));
    }

    @PostMapping
    @PermissionRegister(code = "platform:satori:config", name = "配置 Satori 连接", module = "Satori 平台", desc = "创建 Satori 连接")
    public Result<SatoriConnectionRes> create(@Valid @RequestBody SatoriConnectionCreateRequest request) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.create(SatoriConnectionWebAssembler.toCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:satori:config", name = "配置 Satori 连接", module = "Satori 平台", desc = "更新 Satori 连接")
    public Result<SatoriConnectionRes> update(@PathVariable Long id, @Valid @RequestBody SatoriConnectionUpdateRequest request) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.update(SatoriConnectionWebAssembler.toCmd(id, request))));
    }

    @PostMapping("/{id}/enable")
    @PermissionRegister(code = "platform:satori:connect", name = "启用 Satori 连接", module = "Satori 平台", desc = "启用 Satori 连接")
    public Result<SatoriConnectionRes> enable(@PathVariable Long id) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.enable(id)));
    }

    @PostMapping("/{id}/disable")
    @PermissionRegister(code = "platform:satori:connect", name = "停用 Satori 连接", module = "Satori 平台", desc = "停用 Satori 连接")
    public Result<SatoriConnectionRes> disable(@PathVariable Long id) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.disable(id)));
    }

    @PostMapping("/{id}/test")
    @PermissionRegister(code = "platform:satori:connect", name = "测试 Satori 连接", module = "Satori 平台", desc = "请求 Satori Meta 验证连接")
    public Result<SatoriConnectionTestRes> test(@PathVariable Long id) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.test(id)));
    }

    @GetMapping("/{id}/logs")
    @PermissionRegister(code = "platform:satori:view", name = "查看 Satori 日志", module = "Satori 平台", desc = "查看 Satori 连接运行日志")
    public Result<PageResult<SatoriOperationLogRes>> logs(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        return Result.ok(SatoriConnectionWebAssembler.toLogRes(operationLogAppService.page(id, page, size)));
    }

    @GetMapping(value = "/{id}/logs/stream", produces = "text/event-stream")
    @PermissionRegister(code = "platform:satori:view", name = "订阅 Satori 实时日志", module = "Satori 平台", desc = "订阅指定 Satori 连接的实时运行日志")
    public SseEmitter streamLogs(@PathVariable Long id) {
        operationLogAppService.page(id, 1, 1);
        return liveLogHub.connect(id);
    }

    @GetMapping("/{id}/events/{sequence}")
    @PermissionRegister(code = "platform:satori:view", name = "查看 Satori 完整事件", module = "Satori 平台", desc = "查看已持久化的完整 Satori 原始事件")
    public Result<SatoriEventDetailRes> eventDetail(@PathVariable Long id, @PathVariable String sequence) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(eventAppService.detail(id, sequence)));
    }

    @GetMapping("/{id}/conversations")
    @PermissionRegister(code = "platform:satori:view", name = "查看 Satori 会话", module = "Satori 平台", desc = "查看机器人可访问的群聊会话")
    public Result<SatoriConversationPageRes> conversations(@PathVariable Long id, @RequestParam(required = false) String next) {
        return Result.ok(SatoriConversationWebAssembler.toRes(conversationAppService.conversations(id, next)));
    }

    @PostMapping("/{id}/conversations/direct")
    @PermissionRegister(code = "platform:satori:view", name = "打开 Satori 私聊", module = "Satori 平台", desc = "打开指定好友的私聊频道")
    public Result<SatoriConversationRes> openDirectConversation(@PathVariable Long id, @RequestParam String userId) {
        return Result.ok(SatoriConversationWebAssembler.toRes(conversationAppService.openDirectConversation(id, userId)));
    }

    @GetMapping("/{id}/conversations/{channelId}/messages")
    @PermissionRegister(code = "platform:satori:view", name = "查看 Satori 历史消息", module = "Satori 平台", desc = "查看指定会话的消息历史")
    public Result<SatoriChatMessagePageRes> messages(@PathVariable Long id, @PathVariable String channelId,
                                                      @RequestParam(required = false) String next,
                                                      @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(SatoriConversationWebAssembler.toRes(conversationAppService.messages(id, channelId, next, limit)));
    }

    @GetMapping("/{id}/conversations/members")
    @PermissionRegister(code = "platform:satori:view", name = "查看 Satori 会话成员", module = "Satori 平台", desc = "查看群聊成员以便提及")
    public Result<java.util.List<SatoriChatMemberRes>> members(@PathVariable Long id, @RequestParam(required = false) String guildId,
                                                                @RequestParam(required = false) String next) {
        return Result.ok(SatoriConversationWebAssembler.toMemberRes(conversationAppService.members(id, guildId, next)));
    }

    @GetMapping(value = "/{id}/chat/stream", produces = "text/event-stream")
    @PermissionRegister(code = "platform:satori:view", name = "订阅 Satori 实时消息", module = "Satori 平台", desc = "订阅指定连接的实时聊天消息")
    public SseEmitter streamChat(@PathVariable Long id) {
        conversationAppService.ensureAvailable(id);
        return liveMessageHub.connect(id);
    }

    @PostMapping("/{id}/messages")
    @PermissionRegister(code = "platform:satori:send", name = "发送 Satori 消息", module = "Satori 平台", desc = "向指定 Satori 账号和频道发送消息")
    public Result<SatoriMessageSendRes> send(@PathVariable Long id, @Valid @RequestBody SatoriMessageSendRequest request) {
        return Result.ok(SatoriMessageWebAssembler.toRes(messageDeliveryAppService.deliver(SatoriMessageWebAssembler.toRequest(id, request))));
    }

    @PostMapping(value = "/{id}/messages/media", consumes = "multipart/form-data")
    @PermissionRegister(code = "platform:satori:send", name = "发送 Satori 附件", module = "Satori 平台", desc = "将图片或文件上传到 Satori 适配器并发送")
    public Result<SatoriMessageSendRes> sendMedia(@PathVariable Long id, @RequestParam String platform, @RequestParam String userId,
                                                  @RequestParam String channelId, @RequestParam(required = false) String content,
                                                  @RequestParam("files") MultipartFile[] files) throws IOException {
        return Result.ok(SatoriMessageWebAssembler.toRes(messageDeliveryAppService.deliverMedia(
                SatoriMediaSendWebAssembler.toCmd(id, platform, userId, channelId, content, files))));
    }

    @PostMapping("/{id}/messages/mention-all")
    @PermissionRegister(code = "platform:satori:send", name = "发送 Satori @全体成员消息", module = "Satori 平台", desc = "向群聊发送 @全体成员消息，由 QQ 群内机器人权限决定是否允许")
    public Result<SatoriMessageSendRes> mentionAll(@PathVariable Long id, @Valid @RequestBody SatoriMessageSendRequest request) {
        return Result.ok(SatoriMessageWebAssembler.toRes(messageDeliveryAppService.deliverMentionAll(SatoriMessageWebAssembler.toRequest(id, request))));
    }

    @PostMapping("/{id}/internal")
    @PermissionRegister(code = "platform:satori:internal", name = "调用 Satori 原生接口", module = "Satori 平台", desc = "调用已授权的适配器原生 Satori API")
    public Result<Object> internal(@PathVariable Long id, @Valid @RequestBody SatoriInternalInvokeRequest request) {
        return Result.ok(connectionAppService.invokeInternal(SatoriMessageWebAssembler.toCmd(id, request)));
    }
}
