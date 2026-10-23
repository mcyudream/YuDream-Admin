package online.yudream.base.interfaces.platform.form.support;

import online.yudream.base.application.platform.form.dto.FormSubmissionExportFileDTO;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FormSubmissionExportHttpSupportTest {

    @Test
    void attachmentStreamClosesWhenZipEntryCannotBeWritten() throws Exception {
        TrackedInputStream input = new TrackedInputStream();
        try (ZipOutputStream zip = new ZipOutputStream(new ByteArrayOutputStream()) {
            @Override
            public void putNextEntry(ZipEntry entry) throws IOException {
                throw new IOException("客户端断开");
            }
        }) {
            assertThrows(Exception.class, () -> writeAttachment(zip, input));
        }
        assertTrue(input.closed);
        assertEquals(3, input.available());
    }

    @Test
    void attachmentStreamClosesAfterSuccessfulCopy() throws Exception {
        TrackedInputStream input = new TrackedInputStream();
        try (ZipOutputStream zip = new ZipOutputStream(new ByteArrayOutputStream())) {
            writeAttachment(zip, input);
        }
        assertTrue(input.closed);
        assertEquals(0, input.available());
    }

    private static void writeAttachment(ZipOutputStream zip, TrackedInputStream input) {
        FormSubmissionExportFileDTO file = FormSubmissionExportFileDTO.builder()
                .submissionId(1L).fileId(2L).originalName("报告.txt").build();
        Function<Long, FileContentDTO> loader = id -> FileContentDTO.builder().inputStream(input).build();
        ReflectionTestUtils.invokeMethod(FormSubmissionExportHttpSupport.class, "writeAttachment",
                zip, file, loader, new HashSet<String>());
    }

    private static class TrackedInputStream extends ByteArrayInputStream {
        boolean closed;

        TrackedInputStream() {
            super(new byte[]{1, 2, 3});
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
