package online.yudream.base.interfaces.platform.plugin.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.Part;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingPart;
import online.yudream.base.plugin.spi.http.PluginHttpResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式 HTTP Web 层验证：缓存包装解包（审计被动缓存保持为空、响应缓存不缓冲）、
 * 缓冲读取字符集语义与 Spring @RequestBody String 一致、part 流/临时文件清理、
 * 流式响应直写与提交前后异常行为。
 */
class PluginStreamingHttpWebSupportTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ---------- 缓冲读取：与 @RequestBody String 语义一致 ----------

    @Test
    void bufferedBodyCharsetMatchesStringHttpMessageConverterSemantics() {
        assertEquals(StandardCharsets.UTF_8, PluginStreamingHttpWebSupport.bufferedBodyCharset("application/json"));
        assertEquals(StandardCharsets.UTF_8, PluginStreamingHttpWebSupport.bufferedBodyCharset("application/vnd.api+json"));
        assertEquals(StandardCharsets.ISO_8859_1, PluginStreamingHttpWebSupport.bufferedBodyCharset("text/plain"));
        assertEquals(StandardCharsets.ISO_8859_1, PluginStreamingHttpWebSupport.bufferedBodyCharset(null));
        assertEquals(StandardCharsets.ISO_8859_1, PluginStreamingHttpWebSupport.bufferedBodyCharset("not a media type"));
        // 显式 charset 优先
        assertEquals(StandardCharsets.UTF_16, PluginStreamingHttpWebSupport.bufferedBodyCharset("text/plain;charset=UTF-16"));
        assertEquals(StandardCharsets.ISO_8859_1,
                PluginStreamingHttpWebSupport.bufferedBodyCharset("application/json;charset=ISO-8859-1"));
    }

    @Test
    void bufferedReadDecodesJsonAsUtf8AndEmptyBodyAsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/plugins/x/echo");
        request.setContentType("application/json");
        byte[] payload = "{\"text\":\"端到端\"}".getBytes(StandardCharsets.UTF_8);
        request.setContent(payload);
        assertEquals("{\"text\":\"端到端\"}", PluginStreamingHttpWebSupport.readBufferedBodyAsString(request));

        MockHttpServletRequest empty = new MockHttpServletRequest("GET", "/api/plugins/x/echo");
        empty.setContent(new byte[0]);
        assertNull(PluginStreamingHttpWebSupport.readBufferedBodyAsString(empty), "content-length=0 与 Spring 一致视为无 body");
    }

    @Test
    void bufferedReadReturnsNullForBodylessRequestWithoutContentLength() {
        // 旧 @RequestBody(required=false) 语义回归防护：GET 等没有 Content-Length 且流为空的请求
        // body 必须是 null 而不是 ""（Spring EmptyBodyCheckingHttpInputMessage 对任何空体都返回 null）
        MockHttpServletRequest bodyless = new MockHttpServletRequest("GET", "/api/plugins/x/detail");
        assertEquals(-1, bodyless.getContentLengthLong(), "前置条件：请求未声明 Content-Length");
        assertNull(PluginStreamingHttpWebSupport.readBufferedBodyAsString(bodyless),
                "无 Content-Length 的空体请求不得把 body 读成空字符串");
    }

    // ---------- 流式请求体：绕开被动缓存包装，保留转换包装 ----------

    @Test
    void streamingBodyReadsNativeStreamAndKeepsAuditCacheEmpty() throws IOException {
        MockHttpServletRequest mock = new MockHttpServletRequest("POST", "/api/plugins/x/upload");
        mock.setContentType("application/octet-stream");
        byte[] payload = "raw-body-bytes".getBytes(StandardCharsets.UTF_8);
        mock.setContent(payload);
        ContentCachingRequestWrapper caching = new ContentCachingRequestWrapper(mock);

        var body = PluginStreamingHttpWebSupport.streamingBody(caching);
        assertEquals(payload.length, body.declaredContentLength());
        assertEquals("application/octet-stream", body.contentType());
        try (InputStream in = body.stream()) {
            assertEquals(new String(payload, StandardCharsets.UTF_8),
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        // 审计/日志拦截器读取的被动缓存必须保持为空（流式路径不向其写入任何字节）
        assertEquals(0, caching.getContentAsByteArray().length, "流式读取不得进入缓存包装，否则审计会拿到全量 body");
    }

    @Test
    void streamingBodyKeepsTransformingWrapperLikeDecryption() throws IOException {
        MockHttpServletRequest mock = new MockHttpServletRequest("POST", "/api/plugins/x/echo");
        byte[] cipher = "cipher".getBytes(StandardCharsets.UTF_8);
        mock.setContent(cipher);
        // 模拟解密包装（非 ContentCachingRequestWrapper）：必须保留，不得被剥掉
        HttpServletRequestWrapper decrypting = new HttpServletRequestWrapper(mock) {
            @Override
            public jakarta.servlet.ServletInputStream getInputStream() {
                byte[] plain = "plain".getBytes(StandardCharsets.UTF_8);
                return new jakarta.servlet.ServletInputStream() {
                    private final ByteArrayInputStream in = new ByteArrayInputStream(plain);
                    @Override
                    public int read() {
                        return in.read();
                    }
                    @Override
                    public boolean isFinished() {
                        return in.available() == 0;
                    }
                    @Override
                    public boolean isReady() {
                        return true;
                    }
                    @Override
                    public void setReadListener(jakarta.servlet.ReadListener listener) {
                    }
                };
            }
        };
        var body = PluginStreamingHttpWebSupport.streamingBody(decrypting);
        try (InputStream in = body.stream()) {
            assertEquals("plain", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void streamingBodyStreamIsUniqueAndRejectsReadsAfterClose() throws IOException {
        MockHttpServletRequest mock = new MockHttpServletRequest("POST", "/api/plugins/x/echo");
        mock.setContent("data".getBytes(StandardCharsets.UTF_8));
        var body = PluginStreamingHttpWebSupport.streamingBody(mock);
        InputStream first = body.stream();
        assertSame(first, body.stream(), "重复 stream() 必须返回同一流实例");
        body.close();
        assertThrows(IllegalStateException.class, body::stream, "关闭后不得再读取");
    }

    // ---------- 流式 multipart parts ----------

    @Test
    void streamingPartsExposeLazyStreamsAndDisposeClosesAndDeletes() throws IOException {
        AtomicBoolean fileDeleted = new AtomicBoolean();
        AtomicBoolean fileStreamClosed = new AtomicBoolean();
        AtomicBoolean fieldStreamClosedOnDispose = new AtomicBoolean();
        Part filePart = fakePart("file", "a.bin", "application/octet-stream", 5,
                () -> new CloseTrackingStream("12345".getBytes(StandardCharsets.UTF_8), fileStreamClosed),
                fileDeleted);
        Part fieldPart = fakePart("note", null, "text/plain", 2,
                () -> new CloseTrackingStream("hi".getBytes(StandardCharsets.UTF_8), fieldStreamClosedOnDispose),
                new AtomicBoolean());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/plugins/x/upload");
        request.setContentType("multipart/form-data; boundary=boundary");
        request.addPart(filePart);
        request.addPart(fieldPart);

        Map<String, PluginHttpStreamingPart> parts = PluginStreamingHttpWebSupport.streamingParts(request);
        assertEquals(2, parts.size());
        assertEquals("a.bin", parts.get("file").filename());
        assertEquals(5, parts.get("file").size());
        assertNull(parts.get("note").filename());
        try (InputStream in = parts.get("file").stream()) {
            assertEquals("12345", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        // 模拟插件遗留未关闭的 note 字段流：dispose 必须兜底关闭（懒流未打开则不应被打开，见产品语义）
        parts.get("note").stream();
        // 分发结束：dispose 关闭残余流并删除容器临时文件（幂等）
        parts.values().forEach(PluginHttpStreamingPart::dispose);
        parts.values().forEach(PluginHttpStreamingPart::dispose);
        assertTrue(fileDeleted.get(), "dispose 必须删除容器 spool 临时文件");
        assertTrue(fileStreamClosed.get(), "已被调用方关闭的流在 dispose 后保持关闭状态");
        assertTrue(fieldStreamClosedOnDispose.get(), "dispose 关闭插件遗留的流");
    }

    @Test
    void streamingPartsDisposesStreamsPluginLeftOpen() throws IOException {
        AtomicBoolean streamClosed = new AtomicBoolean();
        AtomicBoolean deleted = new AtomicBoolean();
        Part filePart = fakePart("file", "a.bin", "application/octet-stream", 5,
                () -> new CloseTrackingStream("12345".getBytes(StandardCharsets.UTF_8), streamClosed), deleted);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/plugins/x/upload");
        request.addPart(filePart);
        Map<String, PluginHttpStreamingPart> parts = PluginStreamingHttpWebSupport.streamingParts(request);
        // 模拟插件打开流且不关闭
        parts.get("file").stream();
        assertFalse(streamClosed.get());
        parts.values().forEach(PluginHttpStreamingPart::dispose);
        assertTrue(streamClosed.get(), "dispose 必须兜底关闭插件遗留的 part 流");
        assertTrue(deleted.get());
    }

    @Test
    void streamingPartsWithoutMultipartReturnsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/plugins/x/echo");
        assertTrue(PluginStreamingHttpWebSupport.streamingParts(request).isEmpty());
    }

    // ---------- 流式响应直写：剥离响应缓存包装 ----------

    @Test
    void streamingResponseBypassesCachingResponseWrapperAndClosesBody() throws IOException {
        MockHttpServletResponse nativeResponse = new MockHttpServletResponse();
        ContentCachingResponseWrapper caching = new ContentCachingResponseWrapper(nativeResponse);
        byte[] payload = "streamed-file".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean bodyClosed = new AtomicBoolean();

        PluginHttpDispatchDTO result = PluginHttpDispatchDTO.builder()
                .status(200)
                .contentType("application/octet-stream")
                .headers(new LinkedHashMap<>(Map.of("X-Plugin", "stream")))
                .body(new PluginHttpResponseBody() {
                    @Override
                    public InputStream stream() {
                        return new ByteArrayInputStream(payload);
                    }

                    @Override
                    public long contentLength() {
                        return payload.length;
                    }

                    @Override
                    public void close() {
                        bodyClosed.set(true);
                    }
                })
                .wrapped(false)
                .build();

        PluginStreamingHttpWebSupport.writeStreamingBody(caching, result,
                (PluginHttpResponseBody) result.getBody(), OBJECT_MAPPER);

        // 字节直达原生响应；缓存包装保持为空（接口加密 filter 的 copyBodyToResponse 因此无副作用）
        assertEquals(new String(payload, StandardCharsets.UTF_8), nativeResponse.getContentAsString());
        assertEquals(0, caching.getContentAsByteArray().length, "流式响应不得进入响应缓存包装");
        assertEquals(200, nativeResponse.getStatus());
        assertEquals("stream", nativeResponse.getHeader("X-Plugin"));
        assertEquals("application/octet-stream", nativeResponse.getContentType());
        assertEquals(String.valueOf(payload.length), nativeResponse.getHeader("Content-Length"));
        assertTrue(bodyClosed.get(), "宿主必须在写出后关闭插件响应流");
    }

    @Test
    void streamingResponseWithoutContentLengthOmitsHeader() throws Exception {
        MockHttpServletResponse nativeResponse = new MockHttpServletResponse();
        PluginHttpDispatchDTO result = PluginHttpDispatchDTO.builder()
                .status(200)
                .contentType("text/plain")
                .headers(new LinkedHashMap<>())
                .body(new PluginHttpResponseBody() {
                    @Override
                    public InputStream stream() {
                        return new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public long contentLength() {
                        return -1;
                    }

                    @Override
                    public void close() {
                    }
                })
                .wrapped(false)
                .build();
        PluginStreamingHttpWebSupport.writeStreamingBody(nativeResponse, result,
                (PluginHttpResponseBody) result.getBody(), OBJECT_MAPPER);
        assertNull(nativeResponse.getHeader("Content-Length"), "未知长度不得下发 Content-Length");
        assertEquals("x", nativeResponse.getContentAsString());
    }

    @Test
    void streamingResponsePreCommitFailureWritesJsonError() throws IOException {
        MockHttpServletResponse nativeResponse = new MockHttpServletResponse();
        PluginHttpDispatchDTO result = PluginHttpDispatchDTO.builder()
                .status(200)
                .contentType("application/octet-stream")
                .headers(new LinkedHashMap<>())
                .body(failingBody(false))
                .wrapped(false)
                .build();
        PluginStreamingHttpWebSupport.writeStreamingBody(nativeResponse, result,
                (PluginHttpResponseBody) result.getBody(), OBJECT_MAPPER);
        assertEquals(500, nativeResponse.getStatus());
        assertTrue(nativeResponse.getContentAsString().contains("message"), "提交前失败必须回写 JSON 错误");
    }

    @Test
    void streamingResponsePostCommitFailureNeverWritesAgain() throws IOException {
        MockHttpServletResponse nativeResponse = new MockHttpServletResponse() {
            @Override
            public boolean isCommitted() {
                return true;
            }
        };
        PluginHttpDispatchDTO result = PluginHttpDispatchDTO.builder()
                .status(200)
                .contentType("application/octet-stream")
                .headers(new LinkedHashMap<>())
                .body(failingBody(true))
                .wrapped(false)
                .build();
        // 已提交后失败：不抛出、不二次写 JSON
        PluginStreamingHttpWebSupport.writeStreamingBody(nativeResponse, result,
                (PluginHttpResponseBody) result.getBody(), OBJECT_MAPPER);
        assertEquals("", nativeResponse.getContentAsString());
        assertEquals(200, nativeResponse.getStatus());
    }

    private PluginHttpResponseBody failingBody(boolean assertNotClosedBeforeFailure) {
        return new PluginHttpResponseBody() {
            @Override
            public InputStream stream() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("模拟源中断");
                    }
                };
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public void close() {
            }
        };
    }

    // ---------- 工具 ----------

    private Part fakePart(String name, String filename, String contentType, long size,
                          java.util.function.Supplier<InputStream> streamFactory, AtomicBoolean deleted) {
        return (Part) Proxy.newProxyInstance(Part.class.getClassLoader(), new Class<?>[]{Part.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getSubmittedFileName" -> filename;
                    case "getContentType" -> contentType;
                    case "getSize" -> size;
                    case "getInputStream" -> streamFactory.get();
                    case "delete" -> {
                        deleted.set(true);
                        yield null;
                    }
                    default -> null;
                });
    }

    private static final class CloseTrackingStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final AtomicBoolean closed;

        private CloseTrackingStream(byte[] payload, AtomicBoolean closed) {
            this.delegate = new ByteArrayInputStream(payload);
            this.closed = closed;
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() {
            // 仅记录关闭标记；ByteArrayInputStream.close 本身是 no-op
            closed.set(true);
        }

        @Override
        public int available() {
            return delegate.available();
        }
    }
}
