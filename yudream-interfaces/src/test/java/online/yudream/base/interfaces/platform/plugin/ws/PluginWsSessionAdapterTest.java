package online.yudream.base.interfaces.platform.plugin.ws;

import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话适配器：真非阻塞入队、缓冲上限（含单帧超限）、关闭码/reason 规范化、
 * 二进制拷贝隔离、close 异常吞噬。发送 deadline 强关由桥接生命周期测试覆盖。
 */
class PluginWsSessionAdapterTest {

    /** 直通 executor 桩：execute 即同步执行（AbstractExecutorService，ExecutorService 非函数接口）。 */
    private static java.util.concurrent.ExecutorService directExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
            }

            @Override
            public java.util.List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }
        };
    }
    private final PluginPrincipal principal = new PluginPrincipal(1L, List.of());

    private PluginWsSessionAdapter adapter(StubWebSocketSession session) {
        return new PluginWsSessionAdapter("demo", principal, session, 5000, 512 * 1024, directExecutor());
    }

    @Test
    @DisplayName("sendText 经队列由 writer 下发（直通 executor 下同步完成）")
    void sendTextDelegates() {
        StubWebSocketSession stub = new StubWebSocketSession();
        PluginWsSessionAdapter adapter = adapter(stub);
        adapter.sendText("hello");
        assertEquals(List.of("hello"), stub.sentTexts);
        assertTrue(adapter.isOpen());
        assertEquals("demo", adapter.pluginCode());
        assertEquals(principal, adapter.principal());
    }

    @Test
    @DisplayName("sendBinary 入队前拷贝：排队期间修改原数组不影响已发送内容")
    void sendBinaryCopiesPayload() {
        StubWebSocketSession stub = new StubWebSocketSession();
        PluginWsSessionAdapter adapter = adapter(stub);
        byte[] payload = {1, 2, 3};
        adapter.sendBinary(payload);
        payload[0] = 99;
        assertEquals(1, stub.sentBinaries.get(0)[0]);
    }

    @Test
    @DisplayName("累计缓冲超限：关闭会话（1009）并抛 IllegalStateException")
    void bufferLimitEnforced() {
        StubWebSocketSession stub = new StubWebSocketSession();
        PluginWsSessionAdapter adapter = new PluginWsSessionAdapter(
                "demo", principal, stub, 5000, 16, directExecutor());
        assertThrows(IllegalStateException.class, () -> adapter.sendText("012345678901234567890"));
        assertFalse(adapter.isOpen());
        assertEquals(1009, stub.closedWith.get(0).getCode());
    }

    @Test
    @DisplayName("连续多帧累计远超 buffer：每帧出队即计数回收，会话保持 open 不误判溢出")
    void cumulativeFramesDoNotFalseOverflow() {
        StubWebSocketSession stub = new StubWebSocketSession();
        // 上限 16 字节，每帧 10 字节：8 帧 = 80 字节 >> 16，只要逐帧回收计数就永不溢出
        PluginWsSessionAdapter adapter = new PluginWsSessionAdapter(
                "demo", principal, stub, 5000, 16, directExecutor());
        for (int i = 0; i < 8; i++) {
            adapter.sendText("frame-" + i);
        }
        assertTrue(adapter.isOpen(), "逐帧回收计数后不得误判溢出");
        assertEquals(8, stub.sentTexts.size());
        assertEquals(0, stub.closedWith.size());
    }

    @Test
    @DisplayName("executor 拒绝：清队列、关会话（1011）并抛 IllegalStateException，不悬挂")
    void executorRejectionCleansUp() {
        StubWebSocketSession stub = new StubWebSocketSession();
        PluginWsSessionAdapter adapter = new PluginWsSessionAdapter(
                "demo", principal, stub, 5000, 512 * 1024,
                rejectedExecutor());
        assertThrows(IllegalStateException.class, () -> adapter.sendText("payload"));
        assertFalse(adapter.isOpen());
        assertEquals(1011, stub.closedWith.get(0).getCode());
        // 关闭后再发送直接拒绝
        assertThrows(IllegalStateException.class, () -> adapter.sendText("again"));
    }

    /** 恒拒绝的 executor 桩（ExecutorService 非函数接口，需显式实现）。 */
    private static java.util.concurrent.ExecutorService rejectedExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                throw new java.util.concurrent.RejectedExecutionException("pool down");
            }

            @Override
            public void shutdown() {
            }

            @Override
            public java.util.List<Runnable> shutdownNow() {
                return java.util.List.of();
            }

            @Override
            public boolean isShutdown() {
                return true;
            }

            @Override
            public boolean isTerminated() {
                return true;
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }
        };
    }

    @Test
    @DisplayName("关闭后发送抛 IllegalStateException；closeQuietly 幂等")
    void sendAfterCloseRejected() {
        StubWebSocketSession stub = new StubWebSocketSession();
        PluginWsSessionAdapter adapter = adapter(stub);
        adapter.closeQuietly(1000, "done");
        assertThrows(IllegalStateException.class, () -> adapter.sendText("again"));
        adapter.closeQuietly(1000, "again"); // 不抛
        assertEquals(1, stub.closedWith.size());
    }

    @Test
    @DisplayName("close 规范化非法关闭码与超长 reason；底层 close IOException 被吞")
    void closeSanitizationAndIOExceptionSwallowed() {
        StubWebSocketSession stub = new StubWebSocketSession() {
            @Override
            public void close(org.springframework.web.socket.CloseStatus status) {
                closedWith.add(status);
                throw new IllegalStateException("container already gone");
            }
        };
        PluginWsSessionAdapter adapter = adapter(stub);
        adapter.close(12345, "x".repeat(300));
        assertEquals(1, stub.closedWith.size());
        assertEquals(1011, stub.closedWith.get(0).getCode());
        assertTrue(stub.closedWith.get(0).getReason().length() <= 123);
        assertFalse(adapter.isOpen());
    }

    @Test
    @DisplayName("保留码 1004/1005/1006/1015 与越界码回落 1011，私用段 3000-4999 保留")
    void sanitizeCloseCodeMatrix() {
        assertEquals(1011, PluginWsSessionAdapter.sanitizeCloseCode(999));
        assertEquals(1011, PluginWsSessionAdapter.sanitizeCloseCode(5000));
        assertEquals(1011, PluginWsSessionAdapter.sanitizeCloseCode(1004));
        assertEquals(1011, PluginWsSessionAdapter.sanitizeCloseCode(1005));
        assertEquals(1011, PluginWsSessionAdapter.sanitizeCloseCode(1006));
        assertEquals(1011, PluginWsSessionAdapter.sanitizeCloseCode(1015));
        assertEquals(1000, PluginWsSessionAdapter.sanitizeCloseCode(1000));
        assertEquals(1008, PluginWsSessionAdapter.sanitizeCloseCode(1008));
        assertEquals(4001, PluginWsSessionAdapter.sanitizeCloseCode(4001));
    }

    @Test
    @DisplayName("reason 空安全、UTF-8 码点截断不拆代理对、O(n)")
    void reasonTruncationByCodePoint() {
        assertEquals("", PluginWsSessionAdapter.sanitizeReason(null));
        assertEquals("ok", PluginWsSessionAdapter.sanitizeReason("ok"));
        // 41 个 CJK 字符 = 123 字节恰好放满；42 个 = 126 字节应截到 41 个
        assertEquals("汉".repeat(41), PluginWsSessionAdapter.sanitizeReason("汉".repeat(42)));
        // 代理对（U+1F600，4 字节）：截断处整体保留或整体丢弃
        String truncated = PluginWsSessionAdapter.sanitizeReason("😀".repeat(40));
        assertTrue(truncated.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 123);
        assertTrue(truncated.isEmpty()
                || !Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)));
    }
}
