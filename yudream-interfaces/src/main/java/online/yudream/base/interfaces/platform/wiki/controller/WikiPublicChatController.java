package online.yudream.base.interfaces.platform.wiki.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.service.PublicWikiChatRunAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import online.yudream.base.interfaces.platform.wiki.assembler.PublicWikiChatRunWebAssembler;
import online.yudream.base.interfaces.platform.wiki.assembler.WikiWebAssembler;
import online.yudream.base.interfaces.platform.wiki.request.WikiChatRequest;
import online.yudream.base.interfaces.platform.wiki.res.PublicWikiChatRunRes;
import online.yudream.base.interfaces.platform.wiki.support.PublicWikiChatRunSupport;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/public/wiki/{spaceSlug}/chat")
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"yudream.platform.capabilities.ai.enabled", "yudream.platform.capabilities.wiki.enabled"}, havingValue = "true")
public class WikiPublicChatController {
    private final PublicWikiChatRunSupport execution;
    private final PublicWikiChatRunAppService runs;
    private final AiRunStreamSupport streams;

    @PostMapping("/user/runs")
    @SaCheckLogin
    public Result<AiRunRes> startForUser(@PathVariable String spaceSlug,
            @Valid @RequestBody WikiChatRequest request, HttpServletRequest http) {
        return Result.ok(AiRunWebAssembler.run(execution.startForUser(SecurityPrincipalSupport.current().userId(),
                http.getRemoteAddr(), spaceSlug, request.getQuestion())));
    }

    @PostMapping("/runs")
    public Result<PublicWikiChatRunRes> start(@PathVariable String spaceSlug,
            @RequestHeader(value = "X-Ai-Observer", required = false) String token,
            @Valid @RequestBody WikiChatRequest request, HttpServletRequest http) {
        return Result.ok(PublicWikiChatRunWebAssembler.run(execution.start(http.getRemoteAddr(), spaceSlug, token,
                request.getQuestion(), WikiWebAssembler.chatHistory(request))));
    }

    @GetMapping("/runs/{id}")
    public Result<AiRunRes> get(@PathVariable String spaceSlug, @PathVariable String id,
            @RequestHeader("X-Ai-Observer") String token) {
        return Result.ok(AiRunWebAssembler.run(runs.get(spaceSlug, id, token)));
    }

    @GetMapping(value = "/runs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String spaceSlug, @PathVariable String id,
            @RequestHeader("X-Ai-Observer") String token, @RequestParam(defaultValue = "0") long after) {
        runs.get(spaceSlug, id, token);
        return streams.subscribe(0L, id, after);
    }

    @PostMapping("/runs/{id}/cancel")
    public Result<AiRunRes> cancel(@PathVariable String spaceSlug, @PathVariable String id,
            @RequestHeader("X-Ai-Observer") String token) {
        return Result.ok(AiRunWebAssembler.run(runs.cancel(spaceSlug, id, token)));
    }

    @PostMapping(value = "/agui", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter legacy(@PathVariable String spaceSlug, @Valid @RequestBody WikiChatRequest request,
            HttpServletRequest http) {
        var run = execution.start(http.getRemoteAddr(), spaceSlug, null, request.getQuestion(), WikiWebAssembler.chatHistory(request));
        return streams.subscribe(0L, run.run().id(), 0, true);
    }
}
