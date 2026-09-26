package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingBody;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingDispatchRequest;
import online.yudream.base.plugin.spi.http.PluginHttpBodySizeExceededException;
import online.yudream.base.plugin.spi.http.PluginHttpBodyStream;
import online.yudream.base.plugin.spi.http.PluginHttpResponseBody;
import online.yudream.base.plugin.spi.http.PluginHttpStreamingPart;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.http.PluginStreamingHttpRequest;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流式 HTTP 分发桥接：把宿主内部流式请求映射为 SPI 流式契约并执行插件处理器。
 *
 * <p>执行顺序（安全关键，不可调换）：</p>
 * <ol>
 *   <li>端点鉴权（permission 校验先于任何请求体消费）；</li>
 *   <li>Content-Length 前置限长（超限直接 413，此时尚未读取任何请求体字节，
 *       也不会触发表单/multipart 解析）；</li>
 *   <li>惰性物化 query/body/parts；multipart 由容器解析（磁盘 spool），物化后按
 *       part 总量再次限长；</li>
 *   <li>执行插件处理器：body 为唯一流实例（closed 防重开）并边读边计数限长；
 *       part 流可重复打开，全部被跟踪；</li>
 *   <li>清理请求侧资源（关闭 body 流、dispose part、注销活动分发）：
 *       处理器返回流式响应体时，清理动作随 {@link DeferredCleanupStreamingResponseBody}
 *       移交 Web 写出层，在响应写出结束后触发——请求 body/part 在整个响应写出期间保持可用，
 *       支持请求转响应回显、part 回传等真流式端点；缓冲式/SSE 响应或任何异常路径立即清理。</li>
 * </ol>
 *
 * <p>限长超限抛出 {@link PluginHttpBodySizeExceededException}，本桥接映射为 413 raw 响应；
 * {@code maxBodyBytes <= 0} 表示运维显式关闭限制。其余异常映射与缓冲式分发保持一致
 * （IllegalArgumentException → 400，其余向上抛给全局异常处理器）。</p>
 */
public class PluginStreamingHttpBridge {

    /**
     * 单插件单代活动分发表：由网关在每次插件启用时经 {@link #newRegistry()} 发放并持有，
     * 禁用/卸载时经 {@link #cancel(PluginActiveDispatches)} latch terminated 并关闭全部活动分发。
     * terminated latch 保证"禁用先落、迟到请求后到"的顺序下迟到分发也会被拒绝；
     * 重新启用发放新注册表，旧代迟到请求持有的是已 latch 的旧对象，不会混入新代。
     */
    public static final class PluginActiveDispatches {

        final java.util.Set<ActiveStreamingDispatch> actives = java.util.concurrent.ConcurrentHashMap.newKeySet();
        volatile boolean terminated;
    }

    /** 每次插件启用发放新的一代注册表（由运行时网关持有并绑定当次启用）。 */
    public PluginActiveDispatches newRegistry() {
        return new PluginActiveDispatches();
    }

    /**
     * 禁用/卸载该代全部分发：latch terminated 后关闭请求体/part 流与在途流式响应体。
     * 返回本次取消的分发数量；registry 为 null（该插件从未启用）返回 0。
     */
    public int cancel(PluginActiveDispatches registry) {
        if (registry == null) {
            return 0;
        }
        registry.terminated = true;
        int cancelled = 0;
        for (ActiveStreamingDispatch active : registry.actives) {
            if (active.cancel()) {
                cancelled++;
            }
        }
        registry.actives.clear();
        return cancelled;
    }

    /** 物化 body/parts 之前必须完成的检查（鉴权 + 前置限长）；由网关注入鉴权动作。 */
    public PluginHttpDispatchResult dispatch(
            PluginActiveDispatches registry,
            PluginContextImpl.StreamingHttpRegistration registration,
            String method,
            String path,
            Map<String, List<String>> headers,
            PluginPrincipal principal,
            long maxBodyBytes,
            Runnable permissionCheck,
            PluginHttpStreamingDispatchRequest request
    ) {
        permissionCheck.run();
        long limit = maxBodyBytes > 0 ? maxBodyBytes : Long.MAX_VALUE;
        try {
            return doDispatch(registry, registration, method, path, headers, principal, maxBodyBytes, limit, request);
        } catch (PluginHttpBodySizeExceededException e) {
            // 前置限长（Content-Length 声明阶段，未读取任何字节、未解析表单/multipart）拒绝：映射 413
            return new PluginHttpDispatchResult(413, Map.of(), "application/json",
                    Map.of("message", sizeMessage(e)), false);
        }
    }

    private PluginHttpDispatchResult doDispatch(
            PluginActiveDispatches registry,
            PluginContextImpl.StreamingHttpRegistration registration,
            String method,
            String path,
            Map<String, List<String>> headers,
            PluginPrincipal principal,
            long maxBodyBytes,
            long limit,
            PluginHttpStreamingDispatchRequest request
    ) {
        long declared = declaredContentLength(headers);
        if (declared >= 0 && declared > limit) {
            throw new PluginHttpBodySizeExceededException(maxBodyBytes,
                    "插件流式请求体超过大小限制 " + maxBodyBytes + " 字节（Content-Length=" + declared + "）");
        }
        // 登记活动分发后，物化与处理器执行全程纳入统一 try/finally：任何 supplier（表单/multipart
        // 解析、body 打开）或处理器异常都会释放已物化资源并注销活动分发；仅当清理责任成功转移给
        // 流式响应体包装（deferred）时才延后到响应写出结束后执行。
        ActiveStreamingDispatch active = new ActiveStreamingDispatch();
        registry.actives.add(active);
        BoundedPluginBodyStream spiBody = null;
        Map<String, TrackedPluginStreamingPart> spiParts = Map.of();
        boolean cleanupDeferredToResponse = false;
        try {
            if (registry.terminated) {
                // 与禁用/卸载并发：本次分发立即作废，不消费任何请求体字节
                throw new BizException("插件未启用");
            }
            // 鉴权与前置限长通过后才允许物化表单参数、请求体与 multipart parts。
            // body 取得后立即包装挂接，再物化 parts：任何一阶段失败，finally 都能看到已物化资源
            Map<String, List<String>> query = request.query();
            PluginHttpStreamingBody body = request.body();
            spiBody = body == null ? null : new BoundedPluginBodyStream(body, limit);
            active.attachRequest(spiBody);
            Map<String, online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingPart> parts = request.parts();
            spiParts = trackParts(parts);
            active.attachParts(spiParts.values());
            PluginHttpResponse response;
            if (!spiParts.isEmpty()) {
                long partTotal = spiParts.values().stream().mapToLong(PluginHttpStreamingPart::size).sum();
                if (partTotal > limit) {
                    throw new PluginHttpBodySizeExceededException(maxBodyBytes,
                            "插件流式请求 parts 总大小超过限制 " + maxBodyBytes + " 字节");
                }
            }
            PluginStreamingHttpRequest spiRequest = new PluginStreamingHttpRequest(
                    method, path, headers, query, spiBody, Map.copyOf(spiParts), principal);
            try {
                response = registration.handler().handle(spiRequest);
            } catch (PluginHttpBodySizeExceededException e) {
                response = PluginHttpResponse.rawJson(413, Map.of("message", sizeMessage(e)));
            } catch (IllegalArgumentException e) {
                response = PluginHttpResponse.rawJson(400, Map.of("message", messageOrDefault(e, "请求参数不正确")));
            } catch (RuntimeException e) {
                IllegalArgumentException argumentException = findCause(e, IllegalArgumentException.class);
                if (argumentException != null) {
                    response = PluginHttpResponse.rawJson(400, Map.of("message", messageOrDefault(argumentException, "请求参数不正确")));
                } else {
                    throw e;
                }
            }
            if (response == null) {
                // 与缓冲式分发一致：null 响应视为处理器缺陷，交给全局异常处理器
                throw new IllegalStateException("插件流式 HTTP 端点返回了 null 响应");
            }
            if (response.body() instanceof PluginHttpResponseBody streamingBody) {
                // 真流式响应：请求 body/part 保持可用直到响应写出结束（Web 层 close 响应体时触发清理）
                final BoundedPluginBodyStream cleanupBody = spiBody;
                final Map<String, TrackedPluginStreamingPart> cleanupParts = spiParts;
                DeferredCleanupStreamingResponseBody deferredBody =
                        new DeferredCleanupStreamingResponseBody(streamingBody, () -> {
                            registry.actives.remove(active);
                            closeBodyQuietly(cleanupBody);
                            disposePartsQuietly(cleanupParts);
                        });
                cleanupDeferredToResponse = true;
                active.attachResponse(deferredBody);
                return new PluginHttpDispatchResult(response.status(), response.headers(), response.contentType(),
                        deferredBody, response.wrapped());
            }
            return new PluginHttpDispatchResult(response.status(), response.headers(), response.contentType(),
                    response.body(), response.wrapped());
        } finally {
            if (!cleanupDeferredToResponse) {
                registry.actives.remove(active);
                closeBodyQuietly(spiBody);
                disposePartsQuietly(spiParts);
            }
        }
    }

    /**
     * 活动分发句柄：原子取消状态（CAS 保证 exactly-once），覆盖请求体流、全部 part 流
     * 与在途流式响应体。attach 晚于取消时（双检）立即补关，杜绝取消后响应逃逸。
     */
    private static final class ActiveStreamingDispatch {

        private final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private volatile BoundedPluginBodyStream body;
        private volatile List<TrackedPluginStreamingPart> parts = List.of();
        private volatile PluginHttpResponseBody responseBody;

        /** 物化 body 后挂接；若取消已并发发生则立即补关（关闭幂等）。 */
        void attachRequest(BoundedPluginBodyStream body) {
            this.body = body;
            if (cancelled.get()) {
                closeBodyQuietly();
            }
        }

        /** 物化 parts 后挂接；若取消已并发发生则立即补关已打开的 part 流。 */
        void attachParts(java.util.Collection<TrackedPluginStreamingPart> parts) {
            this.parts = parts == null ? List.of() : List.copyOf(parts);
            if (cancelled.get()) {
                closePartsQuietly();
            }
        }

        /** 处理器返回后挂接流式响应体；若取消已并发发生则立即关闭并拒读。 */
        void attachResponse(PluginHttpResponseBody responseBody) {
            this.responseBody = responseBody;
            if (cancelled.get()) {
                try {
                    responseBody.close();
                } catch (RuntimeException ignored) {
                    // 取消路径的资源释放不向外抛出
                }
            }
        }

        boolean isCancelled() {
            return cancelled.get();
        }

        /** 原子取消：仅首次调用执行关闭；覆盖请求体、part 流与在途响应体。 */
        boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return false;
            }
            closeBodyQuietly();
            closePartsQuietly();
            PluginHttpResponseBody response = responseBody;
            if (response != null) {
                try {
                    response.close();
                } catch (RuntimeException ignored) {
                    // 取消路径的资源释放不向外抛出
                }
            }
            return true;
        }

        private void closeBodyQuietly() {
            BoundedPluginBodyStream bodyStream = body;
            if (bodyStream != null) {
                try {
                    bodyStream.close();
                } catch (RuntimeException | Error ignored) {
                    // 资源清理失败不得掩盖主流程结果
                }
            }
        }

        private void closePartsQuietly() {
            for (TrackedPluginStreamingPart part : parts) {
                try {
                    part.closeOpenedStreams();
                } catch (RuntimeException ignored) {
                    // 残余 part 流兜底关闭
                }
            }
        }
    }

    /**
     * 流式响应体包装：把请求侧资源清理（关闭请求体流、dispose part、注销活动分发）
     * 绑定到响应体关闭动作上。Web 写出层在响应写出结束后统一 close 响应体
     * （含客户端断连、写出失败路径），因此请求 body/part 在整个响应写出期间保持可读。
     * 关闭用 CAS 保证 exactly-once（并发场景：宿主取消 + 写出层 finally）；
     * 关闭后 {@link #stream()} 拒绝读取（含宿主取消后写出层尚未感知的窗口）。
     * 插件禁用/卸载时 {@link #close()} 也被 {@link #cancel(PluginActiveDispatches)} 主动触发。
     */
    static final class DeferredCleanupStreamingResponseBody implements PluginHttpResponseBody {

        private final PluginHttpResponseBody delegate;
        private final Runnable streamCleanup;
        private final java.util.concurrent.atomic.AtomicBoolean closed =
                new java.util.concurrent.atomic.AtomicBoolean();

        DeferredCleanupStreamingResponseBody(PluginHttpResponseBody delegate, Runnable streamCleanup) {
            this.delegate = delegate;
            this.streamCleanup = streamCleanup;
        }

        @Override
        public InputStream stream() {
            if (closed.get()) {
                throw new IllegalStateException("插件流式响应已关闭或被宿主取消，不能读取");
            }
            return delegate.stream();
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                delegate.close();
            } catch (RuntimeException ignored) {
                // 响应流关闭失败不得阻断请求侧资源清理
            }
            streamCleanup.run();
        }
    }

    private String sizeMessage(PluginHttpBodySizeExceededException e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "插件流式请求体超过大小限制 " + e.limitBytes() + " 字节"
                : message;
    }

    private Map<String, TrackedPluginStreamingPart> trackParts(Map<String, online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return Map.of();
        }
        var tracked = new java.util.LinkedHashMap<String, TrackedPluginStreamingPart>(parts.size());
        parts.forEach((name, part) -> tracked.put(name, new TrackedPluginStreamingPart(part)));
        return Map.copyOf(tracked);
    }

    private void closeBodyQuietly(BoundedPluginBodyStream body) {
        if (body != null) {
            try {
                body.close();
            } catch (RuntimeException | Error ignored) {
                // 资源清理失败不得掩盖主流程结果
            }
        }
    }

    private void disposePartsQuietly(Map<String, TrackedPluginStreamingPart> parts) {
        for (TrackedPluginStreamingPart part : parts.values()) {
            try {
                part.dispose();
            } catch (RuntimeException | Error ignored) {
                // 资源清理失败不得掩盖主流程结果
            }
        }
    }

    /** 大小写不敏感读取 Content-Length；缺失或非法（chunked、多值冲突）返回 -1。 */
    private long declaredContentLength(Map<String, List<String>> headers) {
        if (headers == null) {
            return -1;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!"content-length".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                return -1;
            }
            try {
                return Long.parseLong(values.getFirst().trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private String messageOrDefault(Throwable throwable, String fallback) {
        return throwable.getMessage() != null && !throwable.getMessage().isBlank()
                ? throwable.getMessage()
                : fallback;
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * SPI 流式请求体实现：唯一流实例（重复 stream() 返回同一个限长流，closed 后禁止再读）、
     * 边读边计数限长；宿主在分发结束后 close（幂等）。
     */
    static final class BoundedPluginBodyStream implements PluginHttpBodyStream {

        private final PluginHttpStreamingBody delegate;
        private final long limitBytes;
        private volatile boolean closed;
        private volatile LimitedInputStream sharedStream;

        BoundedPluginBodyStream(PluginHttpStreamingBody delegate, long limitBytes) {
            this.delegate = delegate;
            this.limitBytes = limitBytes;
        }

        @Override
        public InputStream stream() {
            if (closed) {
                throw new IllegalStateException("插件流式请求体已关闭，不能再次读取");
            }
            synchronized (this) {
                // 与 close 在同一锁上串行化：delegate.stream() 内触发的回调（如就绪 latch）可能
                // 让宿主在 sharedStream 赋值前发起取消，close 持同锁就不会漏关后创建的底层流
                if (closed) {
                    throw new IllegalStateException("插件流式请求体已关闭，不能再次读取");
                }
                if (sharedStream == null) {
                    sharedStream = new LimitedInputStream(delegate.stream(), limitBytes);
                }
                return sharedStream;
            }
        }

        @Override
        public long declaredContentLength() {
            return delegate.declaredContentLength();
        }

        @Override
        public String contentType() {
            return delegate.contentType();
        }

        @Override
        public void close() {
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                LimitedInputStream stream = sharedStream;
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                        // 底层流关闭失败不影响资源回收语义
                    }
                }
            }
            try {
                delegate.close();
            } catch (RuntimeException ignored) {
                // 宿主兜底关闭，不向外抛出
            }
        }

        boolean isClosed() {
            return closed;
        }
    }

    /** SPI 流式 part 实现：跟踪插件打开的全部流，dispose 时兜底关闭并委托清理临时文件。 */
    static final class TrackedPluginStreamingPart implements PluginHttpStreamingPart {

        private final online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingPart delegate;
        private final List<InputStream> openedStreams = new ArrayList<>();
        /** 关闭/取消标记：置位后拒绝再次打开流（volatile 读为快速路径，登记复核在锁内）。 */
        private volatile boolean closed;

        TrackedPluginStreamingPart(online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingPart delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String filename() {
            return delegate.filename();
        }

        @Override
        public String contentType() {
            return delegate.contentType();
        }

        @Override
        public long size() {
            return delegate.size();
        }

        @Override
        public InputStream stream() throws IOException {
            if (closed) {
                throw new IOException("插件流式 part 已关闭或被宿主取消，不能再次读取");
            }
            InputStream stream = delegate.stream();
            synchronized (openedStreams) {
                // 锁内复核：登记与关闭并发时补关刚打开的流，避免取消后泄漏新流
                if (closed) {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                        // 补关失败按已释放处理
                    }
                    throw new IOException("插件流式 part 已关闭或被宿主取消，不能再次读取");
                }
                openedStreams.add(stream);
            }
            return stream;
        }

        /** 关闭全部已打开流并清理容器临时文件；幂等。 */
        void dispose() {
            closeOpenedStreams();
            try {
                delegate.dispose();
            } catch (RuntimeException ignored) {
                // part 清理失败不向外抛出
            }
        }

        /** 仅兜底关闭插件打开的全部 part 流（不删除临时文件，交给正常 dispose）；宿主取消时唤醒挂起读取。 */
        void closeOpenedStreams() {
            synchronized (openedStreams) {
                closed = true;
                for (InputStream stream : openedStreams) {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                        // 残余流兜底关闭
                    }
                }
                openedStreams.clear();
            }
        }
    }

    /** 边读边计数限长流：超限抛 {@link PluginHttpBodySizeExceededException}（宿主映射 413）。 */
    static final class LimitedInputStream extends FilterInputStream {

        private final long limitBytes;
        private long counted;

        LimitedInputStream(InputStream in, long limitBytes) {
            super(in);
            this.limitBytes = limitBytes;
        }

        long counted() {
            return counted;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0) {
                ensureWithinLimit(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = in.read(buffer, offset, length);
            if (read > 0) {
                ensureWithinLimit(read);
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            long skipped = in.skip(count);
            if (skipped > 0) {
                ensureWithinLimit(skipped);
            }
            return skipped;
        }

        private void ensureWithinLimit(long additional) {
            counted += additional;
            if (counted > limitBytes) {
                throw new PluginHttpBodySizeExceededException(limitBytes,
                        "插件流式请求体超过大小限制 " + limitBytes + " 字节");
            }
        }
    }
}
