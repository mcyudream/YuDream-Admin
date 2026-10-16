package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.WikiChatRunAppService;
import online.yudream.base.domain.platform.wiki.anno.WikiSpaceAccess;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiChatRequest;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Wiki 问答提交同一后台运行日志，页面断开仅结束订阅。 */
@RestController
@RequestMapping("/api/platform/wiki")
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"yudream.platform.capabilities.ai.enabled", "yudream.platform.capabilities.wiki.enabled"}, havingValue = "true")
public class WikiChatController {
    private final WikiChatRunAppService service;
    private final AiRunStreamSupport streams;

    @PostMapping({"/spaces/{spaceId}/chat", "/spaces/{spaceId}/chat/runs"})
    @PermissionRegister(code = "platform:wiki:view", name = "Wiki 智能问答", module = "平台能力", desc = "创建持久后台 Wiki 问答运行")
        @WikiSpaceAccess(action = WikiSpaceAclAction.READ, permission = "platform:wiki:view")
public Result<AiRunRes> chat(@PathVariable Long spaceId, @Valid @RequestBody WikiChatRequest request) {
        var principal = SecurityPrincipalSupport.current();
        return Result.ok(AiRunWebAssembler.run(service.start(principal.userId(), spaceId, request.getSessionId(),
                request.getQuestion(), WikiWebAssembler.chatHistory(request), principal.permissions())));
    }

    @PostMapping(value = {"/spaces/{spaceId}/chat/stream", "/spaces/{spaceId}/chat/agui"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:wiki:view", name = "Wiki 流式问答", module = "平台能力", desc = "订阅持久后台 Wiki 问答运行")
        @WikiSpaceAccess(action = WikiSpaceAclAction.READ, permission = "platform:wiki:view")
public SseEmitter chatStream(@PathVariable Long spaceId, @Valid @RequestBody WikiChatRequest request) {
        var principal = SecurityPrincipalSupport.current();
        var run = service.start(principal.userId(), spaceId, request.getSessionId(), request.getQuestion(),
                WikiWebAssembler.chatHistory(request), principal.permissions());
        return streams.subscribe(principal.userId(), run.id(), 0, true);
    }
}
