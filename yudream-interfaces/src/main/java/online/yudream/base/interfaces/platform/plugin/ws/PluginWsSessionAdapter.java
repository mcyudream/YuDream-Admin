package online.yudream.base.interfaces.platform.plugin.ws;

import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.spi.ws.PluginWsSession;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SPI {@link PluginWsSession} 的宿主实现：真非阻塞发送。
 *
 * <p>发送契约：调用方（插件线程）只做入队（二进制入队前防御性拷贝），
 * 绝不直接写 socket；实际写出由桥接共享受控 executor 上“每会话串行 writer”
 * 完成。队列按字节上限受限：单帧或累计超过上限立即关闭会话（1009）并抛
 * {@link IllegalStateException}，单个大消息同样被入队关口拦截。
 * 底层 {@code sendMessage} 在 writer 线程内执行，不会阻塞宿主关键线程；
 * 写出开始时登记 deadline，超过 {@code sendTimeLimitMs} 未完成的会话由桥接
 * watchdog 以 1011 强制关闭（socket 写不可中断，用关闭连接终止）。
 */
public class PluginWsSessionAdapter implements PluginWsSession {

    private final String pluginCode;
    private final PluginPrincipal principal;
    private final WebSocketSession delegate;
    private final long sendTimeLimitMs;
    private final int sendBufferLimitBytes;
    private final ExecutorService writerPool;

    private final Deque<Frame> queue = new ArrayDeque<>();
    /** 仅统计仍在队列中等待写出的字节数（writer 正在写出的 inflight 帧不计入）。 */
    private long queuedBytes;
    /** 终态标记：close 后 enqueue 一律拒绝；与 queue 同锁维护，避免 close/enqueue 竞态滞留帧。 */
    private boolean closed;
    private final AtomicBoolean draining = new AtomicBoolean();
    /** 当前正在进行的底层写出的截止时间；MIN_VALUE 表示不在写出中。 */
    private final AtomicLong writeDeadlineMillis = new AtomicLong(Long.MIN_VALUE);

    public PluginWsSessionAdapter(String pluginCode, PluginPrincipal principal, WebSocketSession delegate,
                                  long sendTimeLimitMs, int sendBufferLimitBytes, ExecutorService writerPool) {
        this.pluginCode = pluginCode;
        this.principal = principal;
        this.delegate = delegate;
        this.sendTimeLimitMs = Math.max(1, sendTimeLimitMs);
        this.sendBufferLimitBytes = Math.max(1, sendBufferLimitBytes);
        this.writerPool = writerPool;
    }

    @Override
    public String id() {
        return delegate.getId();
    }

    @Override
    public String pluginCode() {
        return pluginCode;
    }

    @Override
    public PluginPrincipal principal() {
        return principal;
    }

    @Override
    public boolean isOpen() {
        // closed 为宿主侧终态标记：close 后即使容器尚未翻转也视为关闭
        return !closed && delegate.isOpen();
    }

    @Override
    public void sendText(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("发送内容不能为空");
        }
        enqueue(new Frame(payload.getBytes(StandardCharsets.UTF_8), true));
    }

    @Override
    public void sendBinary(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("发送内容不能为空");
        }
        // 入队前拷贝：排队期间调用方修改原数组不得改变已发送内容
        enqueue(new Frame(payload.clone(), false));
    }

    private void enqueue(Frame frame) {
        boolean overflow;
        synchronized (queue) {
            if (closed || !isOpen()) {
                throw new IllegalStateException("WebSocket 会话已关闭");
            }
            overflow = queuedBytes + frame.data().length > sendBufferLimitBytes;
            if (!overflow) {
                queue.addLast(frame);
                queuedBytes += frame.data().length;
            }
        }
        if (overflow) {
            closeQuietly(1009, "发送缓冲超限");
            throw new IllegalStateException("发送缓冲超限，会话已关闭");
        }
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (draining.compareAndSet(false, true)) {
            try {
                writerPool.execute(this::drain);
            } catch (RuntimeException e) {
                draining.set(false);
                // executor 拒绝（如容器关闭中）：帧已滞留且无人写出，必须清队列并关会话
                closeQuietly(1011, "发送队列不可用");
                throw new IllegalStateException("发送队列不可用，会话已关闭", e);
            }
        }
    }

    /** writer 线程内串行写出；每帧登记 deadline，写完检查是否超时。 */
    private void drain() {
        try {
            Frame frame;
            while (isOpen() && !closed) {
                synchronized (queue) {
                    frame = queue.pollFirst();
                    if (frame == null) {
                        break;
                    }
                    // 计数仅含 queued（不含 inflight）：出队即减，先于并发 close 的清零
                    queuedBytes -= frame.data().length;
                }
                long start = System.currentTimeMillis();
                writeDeadlineMillis.set(start + sendTimeLimitMs);
                try {
                    delegate.sendMessage(frame.toMessage());
                } catch (IOException | RuntimeException e) {
                    closeQuietly(1001, "发送失败");
                    return;
                } finally {
                    writeDeadlineMillis.set(Long.MIN_VALUE);
                }
                if (System.currentTimeMillis() - start > sendTimeLimitMs) {
                    closeQuietly(1011, "发送超时");
                    return;
                }
            }
        } finally {
            draining.set(false);
            boolean pending;
            synchronized (queue) {
                pending = !queue.isEmpty() && !closed;
            }
            if (pending && isOpen()) {
                scheduleDrain();
            }
        }
    }

    @Override
    public void close(int statusCode, String reason) {
        int code = sanitizeCloseCode(statusCode);
        String safeReason = sanitizeReason(reason);
        synchronized (queue) {
            if (closed) {
                return; // 重复 close 静默忽略（SPI 契约）
            }
            closed = true;
            queue.clear();
            queuedBytes = 0;
        }
        try {
            delegate.close(new org.springframework.web.socket.CloseStatus(code, safeReason));
        } catch (IOException | RuntimeException ignored) {
            // 会话可能已被容器关闭
        }
    }

    /** 静默关闭：生命周期清理路径使用，不得反向抛错；幂等。 */
    public void closeQuietly(int statusCode, String reason) {
        try {
            close(statusCode, reason);
        } catch (RuntimeException ignored) {
            // 关闭路径不得反向抛错
        }
    }

    /** 桥接 watchdog 使用：写出超过 deadline 的会话强制关闭。 */
    void enforceSendDeadline(long nowMillis) {
        long deadline = writeDeadlineMillis.get();
        if (deadline != Long.MIN_VALUE && nowMillis > deadline && isOpen()) {
            closeQuietly(1011, "发送超时");
        }
    }

    /**
     * 关闭码规范化：合法范围 [1000,4999]，且拒绝仅用于帧语义的保留码
     * 1004/1005/1006/1015 与未定义码，越界一律回落 1011。
     */
    static int sanitizeCloseCode(int code) {
        if (code < 1000 || code > 4999) {
            return 1011;
        }
        return switch (code) {
            case 1000, 1001, 1002, 1003, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014 -> code;
            case 1004, 1005, 1006, 1015 -> 1011;
            default -> code >= 3000 ? code : 1011;
        };
    }

    /** reason 以 UTF-8 计长，按码点遍历截断到 123 字节（不截断代理对，O(n)）。 */
    static String sanitizeReason(String reason) {
        if (reason == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(Math.min(reason.length(), 130));
        int byteLength = 0;
        int index = 0;
        while (index < reason.length()) {
            int codePoint = reason.codePointAt(index);
            int encoded = utf8Length(codePoint);
            if (byteLength + encoded > 123) {
                break;
            }
            builder.appendCodePoint(codePoint);
            byteLength += encoded;
            index += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }

    private record Frame(byte[] data, boolean text) {

        org.springframework.web.socket.WebSocketMessage<?> toMessage() {
            return text
                    ? new TextMessage(new String(data, StandardCharsets.UTF_8))
                    : new BinaryMessage(data);
        }
    }
}
