package online.yudream.base.interfaces.platform.plugin.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.Part;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingBody;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingPart;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.plugin.spi.http.PluginHttpResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件流式 HTTP 的 Web 层支撑：
 *
 * <ul>
 *   <li>流式请求体/part 的 Servlet 适配（惰性打开，dispose 时关闭残余流并清理容器临时文件）；</li>
 *   <li>缓冲路径请求体读取（与 {@code @RequestBody String} 的字符集语义一致：
 *       Content-Type charset → JSON 系默认 UTF-8 → 其余 ISO-8859-1，content-length=0 视为无 body）；</li>
 *   <li>流式响应直写原生 Servlet 输出：剥离 {@link ContentCachingResponseWrapper}，
 *       避免整体内存缓冲与接口加密包装；已提交后不再二次写 JSON。写出结束（含失败/断连）
 *       时 close 响应体——运行时网关会把请求 body/part 的释放绑定在该关闭动作上，
 *       保证"边读请求边写响应"的真流式端点可用。</li>
 * </ul>
 *
 * <p>真实行为说明：{@code ContentCachingRequestWrapper} 只被动缓存“已被读取”的字节，
 * 流式路径读原生流时缓存保持为空（审计 body 自然为 none）；接口加密开启时
 * {@code ApiEncryptedRequestWrapper} 位于缓存包装之内、必须保留（解密本身要求全量缓冲），
 * 因此这里只剥离 {@link ContentCachingRequestWrapper} 一层。</p>
 */
public final class PluginStreamingHttpWebSupport {

    private static final Logger log = LoggerFactory.getLogger(PluginStreamingHttpWebSupport.class);
    /** application/*+json 通配类型：与 Spring StringHttpMessageConverter 的 JSON 后缀判定一致。 */
    private static final MediaType APPLICATION_PLUS_JSON = MediaType.valueOf("application/*+json");
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private PluginStreamingHttpWebSupport() {
    }

    /**
     * 缓冲路径请求体读取，复刻 Spring {@code @RequestBody String} 语义：
     * 任何空体都返回 null（无 body）——无 Content-Length 的 GET、content-length=0、chunked 空，
     * 与 Spring {@code EmptyBodyCheckingHttpInputMessage} 的空体判定一致。
     * 经调用链原样读取（保留缓存包装），审计行为与既有路径一致。
     */
    public static String readBufferedBodyAsString(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            if (bytes.length == 0) {
                // 与 Spring @RequestBody(required=false) 一致：空体一律视为无 body 返回 null
                return null;
            }
            return new String(bytes, bufferedBodyCharset(request.getContentType()));
        } catch (IOException e) {
            throw new BizException("读取插件请求体失败：" + e.getMessage());
        }
    }

    /** Content-Type charset → JSON 系（含 application/*+json 后缀）默认 UTF-8 → ISO-8859-1（StringHttpMessageConverter 默认值）。 */
    static Charset bufferedBodyCharset(String contentType) {
        if (StringUtils.hasText(contentType)) {
            try {
                MediaType mediaType = MediaType.parseMediaType(contentType);
                if (mediaType.getCharset() != null) {
                    return mediaType.getCharset();
                }
                if (mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)
                        || mediaType.isCompatibleWith(APPLICATION_PLUS_JSON)) {
                    return StandardCharsets.UTF_8;
                }
            } catch (Exception ignored) {
                // 与 Spring 一致：不可解析的 Content-Type 回落默认字符集
            }
        }
        return StandardCharsets.ISO_8859_1;
    }

    /** 流式请求体：读原生输入流（剥离缓存包装，保留解密等转换包装）。multipart 请求不应使用。 */
    public static PluginHttpStreamingBody streamingBody(HttpServletRequest request) {
        return new ServletStreamingBody(unwrapCachingRequest(request));
    }

    /** 流式 multipart parts：惰性适配 Servlet Part；dispose 时清理临时文件。 */
    public static Map<String, PluginHttpStreamingPart> streamingParts(HttpServletRequest request) {
        try {
            Collection<Part> servletParts = request.getParts();
            if (servletParts == null || servletParts.isEmpty()) {
                return Map.of();
            }
            Map<String, PluginHttpStreamingPart> parts = new LinkedHashMap<>();
            for (Part part : servletParts) {
                parts.put(part.getName(), new ServletStreamingPart(part));
            }
            return parts;
        } catch (IOException | ServletException e) {
            throw new BizException("解析 multipart 请求失败：" + e.getMessage());
        }
    }

    /**
     * 把流式响应体直写原生 Servlet 输出并关闭流。设置状态、headers、Content-Type，
     * contentLength >= 0 时下发 Content-Length。响应体可能是宿主包装
     * （{@code DeferredCleanupStreamingResponseBody}：关闭时同步释放请求 body/part 资源），
     * 因此 finally 中必须始终 close。写出失败时：响应未提交则回写 500 JSON；
     * 已提交（客户端中途断连等）只记录 debug，绝不二次写 JSON。
     * 非受检异常（如插件禁用时宿主主动取消关闭在途响应流、流实现自身的运行时错误）
     * 与 IOException 同样按上述已提交/未提交语义收尾。
     */
    public static void writeStreamingBody(HttpServletResponse response,
                                          PluginHttpDispatchDTO result,
                                          PluginHttpResponseBody streamBody,
                                          ObjectMapper objectMapper) {
        HttpServletResponse target = unwrapCachingResponse(response);
        try {
            target.setStatus(result.getStatus());
            result.getHeaders().forEach(target::addHeader);
            if (StringUtils.hasText(result.getContentType())) {
                target.setContentType(result.getContentType());
            }
            if (streamBody.contentLength() >= 0) {
                target.setContentLengthLong(streamBody.contentLength());
            }
            ServletOutputStream output = target.getOutputStream();
            try (InputStream input = streamBody.stream()) {
                input.transferTo(output);
                output.flush();
            }
        } catch (IOException | RuntimeException e) {
            if (target.isCommitted()) {
                // 客户端断连/写出一半失败：响应已提交，禁止再写 JSON，只能安静收尾
                log.debug("插件流式响应写出中断：status={}", result.getStatus(), e);
            } else {
                writeRawJsonError(target, objectMapper, "插件流式响应写出失败：" + e.getMessage());
            }
        } finally {
            try {
                streamBody.close();
            } catch (Exception e) {
                log.debug("关闭插件流式响应体失败", e);
            }
        }
    }

    private static void writeRawJsonError(HttpServletResponse response, ObjectMapper objectMapper, String message) {
        try {
            response.resetBuffer();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), Map.of("message", message));
        } catch (IOException e) {
            log.debug("插件流式响应错误回写失败", e);
        }
    }

    /** 只剥离被动缓存的 {@link ContentCachingRequestWrapper}，保留解密/multipart 等转换包装。 */
    static HttpServletRequest unwrapCachingRequest(HttpServletRequest request) {
        HttpServletRequest current = request;
        while (current instanceof ContentCachingRequestWrapper caching) {
            current = (HttpServletRequest) ((HttpServletRequestWrapper) caching).getRequest();
        }
        return current;
    }

    /** 只剥离缓冲响应的 {@link ContentCachingResponseWrapper}，避免整体内存缓冲。 */
    static HttpServletResponse unwrapCachingResponse(HttpServletResponse response) {
        HttpServletResponse current = response;
        while (current instanceof ContentCachingResponseWrapper caching) {
            current = (HttpServletResponse) ((HttpServletResponseWrapper) caching).getResponse();
        }
        return current;
    }

    private static final class ServletStreamingBody implements PluginHttpStreamingBody {

        private final HttpServletRequest request;
        private volatile boolean closed;

        private ServletStreamingBody(HttpServletRequest request) {
            this.request = request;
        }

        @Override
        public InputStream stream() {
            if (closed) {
                throw new IllegalStateException("插件流式请求体已关闭，不能再次读取");
            }
            try {
                return request.getInputStream();
            } catch (IOException e) {
                throw new BizException("打开插件请求体失败：" + e.getMessage());
            }
        }

        @Override
        public long declaredContentLength() {
            return request.getContentLengthLong();
        }

        @Override
        public String contentType() {
            return request.getContentType();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** Servlet Part 适配：跟踪打开的流，dispose 时兜底关闭并删除容器临时文件；幂等。 */
    private static final class ServletStreamingPart implements PluginHttpStreamingPart {

        private final Part part;
        private final List<InputStream> openedStreams = new ArrayList<>();
        private volatile boolean disposed;

        private ServletStreamingPart(Part part) {
            this.part = part;
        }

        @Override
        public String name() {
            return part.getName();
        }

        @Override
        public String filename() {
            return part.getSubmittedFileName();
        }

        @Override
        public String contentType() {
            return part.getContentType();
        }

        @Override
        public long size() {
            return part.getSize();
        }

        @Override
        public InputStream stream() throws IOException {
            InputStream stream = part.getInputStream();
            synchronized (openedStreams) {
                openedStreams.add(stream);
            }
            return stream;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            synchronized (openedStreams) {
                for (InputStream stream : openedStreams) {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                        // 残余流兜底关闭
                    }
                }
                openedStreams.clear();
            }
            try {
                // 容器 multipart 磁盘 spool 的临时文件：分发结束即清理（容器请求结束清理前先到先删，幂等）
                part.delete();
            } catch (IOException ignored) {
                // 临时文件可能已被容器清理
            }
        }
    }
}
