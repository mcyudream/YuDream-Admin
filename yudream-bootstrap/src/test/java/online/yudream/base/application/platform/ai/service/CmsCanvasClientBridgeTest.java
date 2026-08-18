package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmsCanvasClientBridgeTest {

    @Test
    void executeWaitsForClientResultAndEnrichesToolCallId() {
        List<String> requests = new CopyOnWriteArrayList<>();
        CmsCanvasClientBridge bridge = new CmsCanvasClientBridge((id, name, args) -> requests.add(id + ":" + name));

        var pool = Executors.newSingleThreadExecutor();
        var future = pool.submit(() -> bridge.execute("cms.canvas.update_text", Map.of("id", "c1", "text", "你好")));
        try {
            // 模拟浏览器执行完成后回帧
            Thread.sleep(80);
            bridge.complete("canvas-1", true, Map.of("message", "文本已更新"), null);
            Map<String, Object> result = future.get();
            assertThat(requests).containsExactly("canvas-1:cms.canvas.update_text");
            assertThat(result).containsEntry("message", "文本已更新").containsEntry("_toolCallId", "canvas-1");
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
        finally {
            pool.shutdownNow();
        }
    }

    @Test
    void clientErrorReturnsErrorResultForModelSelfCorrection() {
        CmsCanvasClientBridge bridge = new CmsCanvasClientBridge((id, name, args) -> {
        });
        var pool = Executors.newSingleThreadExecutor();
        var future = pool.submit(() -> bridge.execute("cms.canvas.remove_component", Map.of("id", "missing")));
        try {
            Thread.sleep(80);
            bridge.complete("canvas-1", false, null, "组件不存在：missing");
            Map<String, Object> result = future.get();
            // 工具级失败作为结果（而非异常）回流模型循环，保证前端工具行闭环、模型可自我修正
            assertThat(result).containsEntry("ok", false).containsEntry("_toolCallId", "canvas-1");
            assertThat(String.valueOf(result.get("error"))).contains("组件不存在");
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
        finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failAllWakesPendingCalls() {
        CmsCanvasClientBridge bridge = new CmsCanvasClientBridge((id, name, args) -> {
        });
        bridge.failAll("运行已结束");
        assertThatThrownBy(() -> bridge.execute("cms.canvas.get_outline", Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("画布连接已断开");
    }
}
