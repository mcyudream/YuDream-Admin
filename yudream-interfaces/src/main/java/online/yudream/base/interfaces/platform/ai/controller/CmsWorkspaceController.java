package online.yudream.base.interfaces.platform.ai.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiAppService;
import online.yudream.base.application.platform.cms.service.CmsWorkspaceStore;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Base64;

/**
 * CMS 静态站点工作区：多页面/资产文件树 IO、builder 对话（Harness 文件工具直执行）
 * 与发布快照。文件内容统一 base64 承载（encoding=text|base64）。
 */
@RestController
@RequestMapping("/api/platform/ai/cms/workspace")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class CmsWorkspaceController {

    private final CmsWorkspaceStore workspaceStore;
    private final AiAppService aiAppService;

    @Value("${yudream.platform.ai.client.sse-timeout:30m}")
    private Duration sseTimeout;

    @GetMapping
    @PermissionRegister(code = "platform:ai:generate", name = "CMS 工作区文件列表", module = "平台能力", desc = "列出 CMS 静态站点工作区文件树")
    public Result<List<CmsWorkspaceStore.WorkspaceEntry>> list() {
        return Result.ok(workspaceStore.list(currentUserId()));
    }

    @GetMapping("/file")
    @PermissionRegister(code = "platform:ai:generate", name = "读取 CMS 工作区文件", module = "平台能力", desc = "读取工作区文本或二进制文件")
    public Result<Map<String, Object>> read(@RequestParam String path) {
        byte[] data = workspaceStore.readFile(currentUserId(), path);
        boolean binary = isBinary(data);
        return Result.ok(Map.of(
                "path", path,
                "encoding", binary ? "base64" : "text",
                "content", binary
                        ? Base64.getEncoder().encodeToString(data)
                        : new String(data, StandardCharsets.UTF_8)));
    }

    @PostMapping("/file")
    @PermissionRegister(code = "platform:ai:generate", name = "写入 CMS 工作区文件", module = "平台能力", desc = "写入工作区文本或二进制文件")
    public Result<Void> write(@RequestBody Map<String, String> body) {
        workspaceStore.writeFile(currentUserId(), body.getOrDefault("path", ""), decode(body));
        return Result.ok(null);
    }

    @PostMapping("/upload")
    @PermissionRegister(code = "platform:ai:generate", name = "上传 CMS 工作区资产", module = "平台能力", desc = "上传二进制资产到工作区")
    public Result<Void> upload(@RequestParam String path, @RequestPart("file") MultipartFile file) throws java.io.IOException {
        workspaceStore.writeFile(currentUserId(), path, file.getBytes());
        return Result.ok(null);
    }

    @PostMapping("/publish")
    @PermissionRegister(code = "platform:ai:generate", name = "发布 CMS 工作区", module = "平台能力", desc = "将工作区整树快照为发布版本")
    public Result<Map<String, Object>> publish() {
        return Result.ok(Map.of("files", workspaceStore.publish(currentUserId())));
    }

    /** builder 对话：Harness 文件工具服务端直执行，AG-UI 事件流与 v1 生成端点同构。 */
    @PostMapping(value = "/{targetKey}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:ai:generate", name = "CMS 工作区 AI 构建", module = "平台能力", desc = "与静态站点构建代理流式对话")
    public SseEmitter chatStream(@PathVariable String targetKey, @Valid @RequestBody WorkspaceChatRequest request) {
        Long userId = SecurityPrincipalSupport.current().userId();
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        String traceId = UUID.randomUUID().toString();
        AtomicInteger toolSequence = new AtomicInteger();
        CompletableFuture.runAsync(() -> {
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicBoolean activityStarted = new AtomicBoolean(false);
            CompletableFuture<Void> heartbeat = null;
            try {
                send(emitter, AiWebAssembler.toAguiRunStarted(traceId));
                heartbeat = startHeartbeat(emitter, traceId, running, activityStarted);
                var result = aiAppService.streamCmsWorkspace(
                        userId,
                        targetKey,
                        request.question(),
                        request.history() == null ? List.of() : request.history(),
                        delta -> send(emitter, AiWebAssembler.toAguiTextChunk(traceId, delta)),
                        reasoning -> send(emitter, AiWebAssembler.toAguiThinkingChunk(traceId, reasoning)),
                        tool -> {
                            String toolCallId = traceId + "-tool-" + toolSequence.incrementAndGet();
                            send(emitter, AiWebAssembler.toAguiToolStart(traceId, toolCallId, tool));
                            send(emitter, AiWebAssembler.toAguiToolResult(traceId, toolCallId, tool));
                        },
                        progress -> sendActivity(emitter, traceId, activityStarted, progress.action(), progress.content()));
                send(emitter, AiWebAssembler.toAguiRunFinished(traceId, result));
                emitter.complete();
            } catch (Exception e) {
                String message = e instanceof online.yudream.base.domain.common.exception.BizException biz
                        ? biz.getMessage()
                        : "生成失败，请稍后重试（traceId=" + traceId + "）";
                send(emitter, AiWebAssembler.toAguiRunError(traceId, message));
                emitter.complete();
            } finally {
                running.set(false);
                if (heartbeat != null) {
                    heartbeat.cancel(true);
                }
            }
        });
        return emitter;
    }

    public record WorkspaceChatRequest(
            @NotBlank(message = "问题不能为空") String question,
            List<online.yudream.base.domain.platform.ai.valobj.AiChatMessage> history) {
    }

    /** 框架运行时库：静态页一行 <script src> 接入 data-yb-* 渲染与后端数据互通。 */
    @GetMapping(value = "/lib.js", produces = "application/javascript;charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> lib() throws java.io.IOException {
        byte[] body = new ClassPathResource("cms/cms-lib.js").getInputStream().readAllBytes();
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript;charset=UTF-8"))
                .header("Cache-Control", "public, max-age=300")
                .body(body);
    }

    private static void send(SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // 客户端断开由容器回调处理
        }
    }

    private static void sendActivity(SseEmitter emitter, String traceId, AtomicBoolean started,
                                     String phase, String content) {
        if (started.compareAndSet(false, true) || true) {
            send(emitter, AiWebAssembler.toAguiActivityDelta(traceId, phase, content));
        }
    }

    private static CompletableFuture<Void> startHeartbeat(
            SseEmitter emitter, String traceId, AtomicBoolean running, AtomicBoolean activityStarted) {
        return CompletableFuture.runAsync(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(4000);
                    sendActivity(emitter, traceId, activityStarted, "heartbeat", "仍在生成中…");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    private static Long currentUserId() {
        return SecurityPrincipalSupport.current().userId();
    }

    private static byte[] decode(Map<String, String> body) {
        String content = body.getOrDefault("content", "");
        return "base64".equals(body.get("encoding"))
                ? Base64.getDecoder().decode(content)
                : content.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isBinary(byte[] data) {
        int limit = Math.min(data.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
