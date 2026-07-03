package online.yudream.base.infra.platform.document.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.service.WordTemplateRenderer;
import online.yudream.base.domain.platform.document.valobj.RenderedDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class DocxWordTemplateRenderer implements WordTemplateRenderer {

    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public RenderedDocument render(InputStream templateInputStream, Map<String, String> data) {
        try (InputStream inputStream = templateInputStream;
             XWPFDocument document = new XWPFDocument(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            replaceParagraphs(document.getParagraphs(), data);
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        replaceParagraphs(cell.getParagraphs(), data);
                    }
                }
            }
            document.write(outputStream);
            return new RenderedDocument(outputStream.toByteArray(), CONTENT_TYPE);
        }
        catch (Exception e) {
            throw new BizException("DOCX 模板渲染失败：" + e.getMessage());
        }
    }

    private void replaceParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> data) {
        for (XWPFParagraph paragraph : paragraphs) {
            replaceParagraph(paragraph, data);
        }
    }

    private void replaceParagraph(XWPFParagraph paragraph, Map<String, String> data) {
        String text = paragraph.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        String replaced = replacePlaceholders(text, data);
        if (text.equals(replaced)) {
            return;
        }
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }
        XWPFRun firstRun = runs.get(0);
        for (int i = runs.size() - 1; i >= 1; i--) {
            paragraph.removeRun(i);
        }
        firstRun.setText(replaced, 0);
    }

    private String replacePlaceholders(String text, Map<String, String> data) {
        String result = text;
        for (Map.Entry<String, String> entry : (data == null ? Map.<String, String>of() : data).entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
