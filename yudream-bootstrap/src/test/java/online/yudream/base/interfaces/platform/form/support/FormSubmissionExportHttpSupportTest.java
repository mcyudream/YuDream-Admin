package online.yudream.base.interfaces.platform.form.support;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import online.yudream.base.application.platform.form.dto.DynamicFormDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionExportDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionExportFileDTO;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FormSubmissionExportHttpSupportTest {

    private static final String SCHEMA = """
            [
              {"type": "input", "field": "name", "title": "姓名"},
              {"type": "upload", "field": "attachment", "title": "附件"}
            ]
            """;

    @Test
    void writesPlainExcelWhenNoAttachments() throws Exception {
        CapturingResponse response = new CapturingResponse();

        FormSubmissionExportHttpSupport.write(response.http, export(List.of()), id -> null);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", response.contentType);
        assertTrue(response.contentDisposition.endsWith(".xlsx"));
        assertTrue(response.body.size() > 0);
    }

    @Test
    void packsExcelAndAttachmentsIntoZip() throws Exception {
        CapturingResponse response = new CapturingResponse();
        List<FormSubmissionExportFileDTO> files = List.of(
                file(101L, "需求文档.docx"),
                file(102L, "需求文档.docx")
        );

        FormSubmissionExportHttpSupport.write(response.http, export(files), id ->
                FileContentDTO.builder()
                        .originalName("ignored")
                        .inputStream(new ByteArrayInputStream(("content-" + id).getBytes(StandardCharsets.UTF_8)))
                        .build());

        assertEquals("application/zip", response.contentType);
        assertTrue(response.contentDisposition.endsWith(".zip"));
        List<String> entries = zipEntries(response.body.toByteArray());
        assertEquals("反馈收集-提交结果.xlsx", entries.get(0));
        assertTrue(entries.contains("附件/11/需求文档.docx"));
        assertTrue(entries.contains("附件/11/需求文档 (2).docx"));
    }

    @Test
    void skipsAttachmentsThatFailToLoad() throws Exception {
        CapturingResponse response = new CapturingResponse();

        FormSubmissionExportHttpSupport.write(response.http, export(List.of(file(101L, "已删除.txt"))), id -> {
            throw new IllegalStateException("gone");
        });

        List<String> entries = zipEntries(response.body.toByteArray());
        assertEquals(List.of("反馈收集-提交结果.xlsx"), entries);
    }

    private FormSubmissionExportFileDTO file(Long fileId, String originalName) {
        return FormSubmissionExportFileDTO.builder()
                .submissionId(11L).field("attachment").fileId(fileId).originalName(originalName)
                .build();
    }

    private FormSubmissionExportDTO export(List<FormSubmissionExportFileDTO> files) {
        return FormSubmissionExportDTO.builder()
                .form(DynamicFormDTO.builder().name("反馈收集").schemaJson(SCHEMA).build())
                .submissions(List.of(FormSubmissionDTO.builder()
                        .id(11L)
                        .data(Map.of("name", "张三", "attachment", "/api/files/101/content"))
                        .build()))
                .files(files)
                .build();
    }

    private List<String> zipEntries(byte[] bytes) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static final class CapturingResponse {
        private final HttpServletResponse http = mock(HttpServletResponse.class);
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private String contentType;
        private String contentDisposition;

        private CapturingResponse() throws IOException {
            ServletOutputStream stream = new ServletOutputStream() {
                @Override
                public void write(int b) {
                    body.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                }
            };
            when(http.getOutputStream()).thenReturn(stream);
            org.mockito.Mockito.doAnswer(invocation -> contentType = invocation.getArgument(0))
                    .when(http).setContentType(org.mockito.ArgumentMatchers.anyString());
            org.mockito.Mockito.doAnswer(invocation -> contentDisposition = invocation.getArgument(1))
                    .when(http).setHeader(org.mockito.ArgumentMatchers.eq("Content-Disposition"), org.mockito.ArgumentMatchers.anyString());
        }
    }
}
