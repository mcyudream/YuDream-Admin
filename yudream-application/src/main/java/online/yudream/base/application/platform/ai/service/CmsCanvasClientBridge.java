package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.common.exception.BizException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一次 CMS Agent 运行的客户端工具桥：工具在浏览器画布侧真实执行，服务端在此挂起等待
 * 结果帧返回后继续模型循环——coding-agent 式 read-after-write 闭环的核心。
 */
public final class CmsCanvasClientBridge {

    /** 工具调用超时：前端执行画布操作通常毫秒级，180 秒足以覆盖异常卡顿。 */
    private static final long TIMEOUT_SECONDS = 180;

    @FunctionalInterface
    public interface Outbound {
        void send(String toolCallId, String toolName, Map<String, Object> args);
    }

    private final Outbound outbound;
    private final AtomicInteger sequence = new AtomicInteger();
    private final ConcurrentMap<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean failed = new AtomicBoolean(false);

    public CmsCanvasClientBridge(Outbound outbound) {
        this.outbound = outbound;
    }

    /** 由模型工具循环调用：向浏览器发出工具请求并等待真实执行结果。 */
    public Map<String, Object> execute(String toolName, Map<String, Object> args) {
        if (failed.get()) {
            throw new BizException("画布连接已断开，无法执行工具：" + toolName);
        }
        String toolCallId = "canvas-" + sequence.incrementAndGet();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pending.put(toolCallId, future);
        try {
            outbound.send(toolCallId, toolName, args == null ? Map.of() : args);
            Map<String, Object> result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // 结果附带 toolCallId，供 TOOL_CALL_RESULT 事件与前端请求行关联。
            Map<String, Object> enriched = new java.util.LinkedHashMap<>(result == null ? Map.of() : result);
            enriched.put("_toolCallId", toolCallId);
            return enriched;
        }
        catch (TimeoutException e) {
            throw new BizException("画布工具响应超时：" + toolName);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("画布工具等待被中断：" + toolName);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new BizException(cause.getMessage() == null ? "画布工具执行失败：" + toolName : cause.getMessage());
        }
        finally {
            pending.remove(toolCallId);
        }
    }

    /** 浏览器回帧：ok=false 时 error 作为工具报错抛给模型，让模型自行修正。 */
    public void complete(String toolCallId, boolean ok, Map<String, Object> result, String error) {
        CompletableFuture<Map<String, Object>> future = pending.get(toolCallId);
        if (future == null) {
            return;
        }
        if (ok) {
            future.complete(result == null ? Map.of() : result);
        }
        else {
            future.completeExceptionally(new BizException(error == null || error.isBlank() ? "画布工具执行失败" : error));
        }
    }

    /** 连接断开或运行结束时唤醒所有挂起调用，避免模型循环卡死。 */
    public void failAll(String reason) {
        failed.set(true);
        pending.values().forEach(future -> future.completeExceptionally(new BizException(reason)));
    }
}
