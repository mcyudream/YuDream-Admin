package online.yudream.base.infra.platform.document.service;

import online.yudream.base.plugin.spi.system.document.PluginWordTemplateImage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Word 模板渲染器图片通道：独立段落占位符嵌图、表格行循环嵌图、文本上下文降级标记。 */
class DocxWordTemplateRendererTest {

    private final DocxWordTemplateRenderer renderer = new DocxWordTemplateRenderer();

    @Test
    void embedsImageForStandalonePlaceholderParagraph() throws Exception {
        byte[] docx = renderer.render(new ByteArrayInputStream(template(
                "标题：{{title}}",
                "{{cover}}",
                "结尾")), Map.of(
                "title", "活动",
                "cover", PluginWordTemplateImage.png(pngBytes(120, 80)))).content();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertEquals(1, document.getAllPictures().size());
            assertTrue(document.getAllPictures().get(0).getData().length > 0);
            String text = paragraphText(document);
            assertFalse(text.contains("{{cover}}"));
            assertTrue(text.contains("标题：活动"));
            assertTrue(text.contains("结尾"));
        }
    }

    @Test
    void embedsImagesInsideTableRowLoop() throws Exception {
        // 行循环：标记行 + 模板行（说明列 + 图片列各占一格，图片独占单元格）
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("效果展示");
            XWPFTable table = document.createTable(3, 2);
            table.getRow(0).getCell(0).setText("{{#images}}");
            table.getRow(1).getCell(0).setText("{{caption}}");
            table.getRow(1).getCell(1).setText("{{img}}");
            table.getRow(2).getCell(0).setText("{{/images}}");
            document.write(buffer);
            byte[] docx = renderer.render(new ByteArrayInputStream(buffer.toByteArray()), Map.of(
                    "images", List.of(
                            Map.of("caption", "图一", "img", PluginWordTemplateImage.png(pngBytes(60, 40))),
                            Map.of("caption", "图二", "img", PluginWordTemplateImage.png(pngBytes(30, 90)))
                    ))).content();
            try (XWPFDocument rendered = new XWPFDocument(new ByteArrayInputStream(docx))) {
                assertEquals(2, rendered.getAllPictures().size());
                assertFalse(fullText(rendered).contains("{{#images}}"));
                assertFalse(fullText(rendered).contains("{{img}}"));
                assertTrue(fullText(rendered).contains("图一"));
                assertTrue(fullText(rendered).contains("图二"));
            }
        }
    }

    @Test
    void imageValueInTextContextRendersMarkerInsteadOfGarbage() throws Exception {
        byte[] docx = renderer.render(new ByteArrayInputStream(template("行内：{{cover}} 结束")),
                Map.of("cover", PluginWordTemplateImage.png(pngBytes(10, 10)))).content();
        String text = paragraphText(docx);
        assertTrue(text.contains("[图片]"));
        assertFalse(text.contains("PluginWordTemplateImage"));
    }

    @Test
    void rejectsUnsupportedFormatAndBadWidth() {
        assertTrue(PluginWordTemplateImage.isSupportedFormat("JPEG"));
        assertFalse(PluginWordTemplateImage.isSupportedFormat("webp"));
        assertThrows(IllegalArgumentException.class,
                () -> new PluginWordTemplateImage(new byte[]{1}, "webp", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PluginWordTemplateImage(pngBytes(2, 2), "png", 0));
    }

    // ---------- 工具 ----------

    private byte[] template(String... lines) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            for (String line : lines) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(buffer);
            return buffer.toByteArray();
        }
    }

    private String paragraphText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            text.append(paragraph.getText()).append('\n');
        }
        return text.toString();
    }

    private String paragraphText(byte[] docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            return paragraphText(document);
        }
    }

    private String fullText(XWPFDocument document) {
        StringBuilder text = new StringBuilder(paragraphText(document));
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                row.getTableCells().forEach(cell -> text.append(cell.getTextRecursively()).append('\n'));
            }
        }
        return text.toString();
    }

    private byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(width % 255, height % 255, 90));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }
}
