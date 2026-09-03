package online.yudream.base.interfaces.platform.preview;

import online.yudream.base.application.platform.preview.service.PluginPreviewFileOpener;
import online.yudream.base.interfaces.platform.preview.controller.PublicFilePreviewController;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 签名公开文件端点回归：返回类型必须是 ResponseEntity&lt;StreamingResponseBody&gt;，
 * 通配符声明会让 Spring 走消息转换器路径抛 HttpMessageNotWritableException（实机 500）。
 */
@ExtendWith(MockitoExtension.class)
class PublicFilePreviewControllerTest {

    private static final String URL = "/api/public/preview/file/tok/x.png";

    @Mock
    private PluginPreviewFileOpener previewFileOpener;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicFilePreviewController(previewFileOpener)).build();
    }

    @Test
    void streamsSignedFileContent() throws Exception {
        byte[] bytes = "png-bytes".getBytes(StandardCharsets.UTF_8);
        when(previewFileOpener.openSignedFile("tok")).thenReturn(Optional.of(new PluginStoredFile(
                "materials/1/v1/file", "image/png", (long) bytes.length, new ByteArrayInputStream(bytes))));

        MvcResult result = mockMvc.perform(get(URL))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(1000);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void returnsNotFoundWhenTokenInvalid() throws Exception {
        when(previewFileOpener.openSignedFile("tok")).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(get(URL))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(1000);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("签名无效、已过期或文件不存在"));
    }

    @Test
    void servesPartialContentForRange() throws Exception {
        byte[] bytes = "0123456789".getBytes(StandardCharsets.UTF_8);
        when(previewFileOpener.openSignedFile("tok")).thenReturn(Optional.of(new PluginStoredFile(
                "materials/1/v1/file", "application/octet-stream", (long) bytes.length,
                new ByteArrayInputStream(bytes))));

        MvcResult result = mockMvc.perform(get(URL).header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(1000);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(content().bytes("2345".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsUnsatisfiableRange() throws Exception {
        byte[] bytes = "0123456789".getBytes(StandardCharsets.UTF_8);
        when(previewFileOpener.openSignedFile("tok")).thenReturn(Optional.of(new PluginStoredFile(
                "materials/1/v1/file", "application/octet-stream", (long) bytes.length,
                new ByteArrayInputStream(bytes))));

        MvcResult result = mockMvc.perform(get(URL).header(HttpHeaders.RANGE, "bytes=99-100"))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(1000);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */10"))
                .andExpect(jsonPath("$.message").value("Range 不合法"));
    }
}
