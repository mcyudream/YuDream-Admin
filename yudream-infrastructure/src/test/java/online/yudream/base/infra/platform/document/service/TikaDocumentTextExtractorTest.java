package online.yudream.base.infra.platform.document.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.valobj.DocumentSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TikaDocumentTextExtractorTest {

    private final TikaDocumentTextExtractor extractor = new TikaDocumentTextExtractor(10 * 1024 * 1024L);

    @Test
    void extractsBase64TextDataUrl() {
        String encoded = Base64.getEncoder().encodeToString("Agent 文档内容".getBytes(StandardCharsets.UTF_8));

        String text = extractor.extract(DocumentSource.dataUrl("data:text/plain;charset=UTF-8;base64," + encoded));

        assertEquals("Agent 文档内容", text.trim());
    }

    @Test
    void extractsRawBase64TextWithMediaType() {
        String encoded = Base64.getEncoder().encodeToString("raw base64 text".getBytes(StandardCharsets.UTF_8));

        String text = extractor.extract(DocumentSource.base64(encoded, "text/plain", "note.txt"));

        assertEquals("raw base64 text", text.trim());
    }

    @Test
    void extractsPercentEncodedTextDataUrl() {
        String text = extractor.extract(DocumentSource.dataUrl("data:text/plain,plain%20data%20url"));

        assertEquals("plain data url", text.trim());
    }

    @Test
    void extractsPdfText() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("workflow pdf content");
                content.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }

        String text = extractor.extract(DocumentSource.base64(
                Base64.getEncoder().encodeToString(pdf), "application/pdf", "workflow.pdf"));

        assertTrue(text.contains("workflow pdf content"));
    }

    @Test
    void extractsDocxText() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("workflow docx content");
            document.write(output);
            docx = output.toByteArray();
        }

        String text = extractor.extract(DocumentSource.base64(
                Base64.getEncoder().encodeToString(docx),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "workflow.docx"));

        assertTrue(text.contains("workflow docx content"));
    }

    @Test
    void rejectsEmptyContent() {
        BizException exception = assertThrows(BizException.class,
                () -> extractor.extract(new DocumentSource(" ", "text/plain", "empty.txt")));

        assertEquals("文档内容不能为空", exception.getMessage());
    }

    @Test
    void rejectsContentLargerThanConfiguredLimit() {
        TikaDocumentTextExtractor limited = new TikaDocumentTextExtractor(4);
        String encoded = Base64.getEncoder().encodeToString("12345".getBytes(StandardCharsets.UTF_8));

        BizException exception = assertThrows(BizException.class,
                () -> limited.extract(DocumentSource.base64(encoded, "text/plain", "large.txt")));

        assertEquals("文档大小不能超过 4 字节", exception.getMessage());
    }

    @Test
    void rejectsInvalidBase64Input() {
        BizException exception = assertThrows(BizException.class,
                () -> extractor.extract(DocumentSource.base64("not@base64", "text/plain", "broken.txt")));

        assertEquals("文档输入不是有效的 Data URL 或 Base64 内容", exception.getMessage());
    }

    @Test
    void rejectsMalformedDataUrl() {
        BizException exception = assertThrows(BizException.class,
                () -> extractor.extract(DocumentSource.dataUrl("data:text/plain,broken%2")));

        assertEquals("文档输入不是有效的 Data URL 或 Base64 内容", exception.getMessage());
    }

    @Test
    void rejectsUnsupportedBinaryInput() {
        String encoded = Base64.getEncoder().encodeToString(new byte[]{0, 1, 2, 3, 4, 5});

        BizException exception = assertThrows(BizException.class,
                () -> extractor.extract(DocumentSource.base64(encoded, "application/x-yudream-unsupported", "data.bin")));

        assertEquals("不支持的文档类型：application/x-yudream-unsupported", exception.getMessage());
    }
}
