package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiChatAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiKnowledgeWebAssembler;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiChatRequest;
import online.yudream.base.interfaces.platform.wiki.res.WikiChatResultRes;
import online.yudream.base.interfaces.platform.wiki.support.WikiChatExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

/**
 * Wiki 管理端问答接口，仅负责边界校验、应用服务接线和响应装配。
 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
public class WikiChatController {

    private final WikiChatAppService service;
    private final WikiChatExecution execution;

    @Value("${yudream.platform.wiki.chat.sse-timeout:30m}")
    private Duration sseTimeout;

    @PostMapping("/spaces/{spaceId}/chat")
    @PermissionRegister(code = "platform:wiki:view", name = "Wiki 智能问答", module = "平台能力", desc = "基于知识库内容的 LLM 问答")
    public Result<WikiChatResultRes> chat(@PathVariable Long spaceId,
                                          @Valid @RequestBody WikiChatRequest request) {
        return Result.ok(WikiKnowledgeWebAssembler.toRes(
                service.chat(spaceId, request.getQuestion(), WikiWebAssembler.chatHistory(request))));
    }

    /**
     * 兼容旧 SSE：正文、reasoning、工具、引用分别使用独立事件，reasoning 不伪装成 activity。
     */
    @PostMapping(value = "/spaces/{spaceId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:wiki:view", name = "Wiki 流式问答", module = "平台能力", desc = "基于知识库内容的 LLM 流式问答")
    public SseEmitter chatStream(@PathVariable Long spaceId,
                                 @Valid @RequestBody WikiChatRequest request) {
        return execution.startLegacy(sseTimeout, spaceId, (onDelta, onReasoning, onTool, onActivity) ->
                service.chatStream(
                        spaceId,
                        request.getQuestion(),
                        WikiWebAssembler.chatHistory(request),
                        onDelta,
                        onReasoning,
                        onTool,
                        onActivity)).emitter();
    }

    @PostMapping(value = "/spaces/{spaceId}/chat/agui", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:wiki:view", name = "Wiki 智能问答（AG-UI）", module = "平台能力", desc = "基于知识库内容的 LLM 流式问答（AG-UI 协议）")
    public SseEmitter chatAgui(@PathVariable Long spaceId,
                               @Valid @RequestBody WikiChatRequest request) {
        return execution.startAgui(sseTimeout, spaceId, (onDelta, onReasoning, onTool, onActivity) ->
                service.chatStream(
                        spaceId,
                        request.getQuestion(),
                        WikiWebAssembler.chatHistory(request),
                        onDelta,
                        onReasoning,
                        onTool,
                        onActivity)).emitter();
    }
}
