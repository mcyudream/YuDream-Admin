package online.yudream.base.infra.platform.document.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.service.WordTemplateRenderer;
import online.yudream.base.domain.platform.document.valobj.RenderedDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocxWordTemplateRenderer implements WordTemplateRenderer {

    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Pattern LOOP_START = Pattern.compile("\\{\\{#\\s*([A-Za-z0-9_.-]+)\\s*}}");
    private static final Pattern INLINE_LOOP = Pattern.compile("\\{\\{#\\s*([A-Za-z0-9_.-]+)\\s*}}([\\s\\S]*?)\\{\\{/\\s*\\1\\s*}}");
    private static final Pattern MUSTACHE_VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");
    private static final Pattern DOLLAR_VARIABLE = Pattern.compile("\\$\\{\\s*([A-Za-z0-9_.-]+)\\s*}");
    private static final String PARTICIPANTS_KEY = "participants";

    @Override
    public RenderedDocument render(InputStream templateInputStream, Map<String, Object> data) {
        Map<String, Object> safeData = data == null ? Map.of() : data;
        try (InputStream inputStream = templateInputStream;
             XWPFDocument document = new XWPFDocument(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            appendParticipantTables(document.getTables(), safeData);
            replaceParagraphs(document.getParagraphs(), safeData);
            replaceTables(document.getTables(), safeData);
            for (XWPFHeader header : document.getHeaderList()) {
                appendParticipantTables(header.getTables(), safeData);
                replaceParagraphs(header.getParagraphs(), safeData);
                replaceTables(header.getTables(), safeData);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                appendParticipantTables(footer.getTables(), safeData);
                replaceParagraphs(footer.getParagraphs(), safeData);
                replaceTables(footer.getTables(), safeData);
            }
            document.write(outputStream);
            return new RenderedDocument(outputStream.toByteArray(), CONTENT_TYPE);
        }
        catch (Exception e) {
            throw new BizException("DOCX 模板渲染失败：" + e.getMessage());
        }
    }

    private void replaceTables(List<XWPFTable> tables, Map<String, Object> data) {
        for (XWPFTable table : tables) {
            renderTableRowLoops(table, data);
            for (XWPFTableRow row : table.getRows()) {
                replaceRow(row, data);
            }
        }
    }

    private void replaceRow(XWPFTableRow row, Map<String, Object> data) {
        for (XWPFTableCell cell : row.getTableCells()) {
            replaceParagraphs(cell.getParagraphs(), data);
            replaceTables(cell.getTables(), data);
        }
    }

    private void replaceParagraphs(List<XWPFParagraph> paragraphs, Map<String, Object> data) {
        for (XWPFParagraph paragraph : paragraphs) {
            replaceParagraph(paragraph, data);
        }
    }

    private void replaceParagraph(XWPFParagraph paragraph, Map<String, Object> data) {
        String text = paragraph.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        String replaced = replacePlaceholders(replaceInlineLoops(text, data), data);
        if (text.equals(replaced)) {
            return;
        }
        setParagraphText(paragraph, replaced);
    }

    private void renderTableRowLoops(XWPFTable table, Map<String, Object> data) {
        int index = 0;
        while (index < table.getRows().size()) {
            XWPFTableRow row = table.getRows().get(index);
            Matcher startMatcher = LOOP_START.matcher(rowText(row));
            if (!startMatcher.find()) {
                index++;
                continue;
            }

            String collectionKey = startMatcher.group(1);
            int endIndex = findLoopEndRow(table, index, collectionKey);
            if (endIndex < index) {
                index++;
                continue;
            }

            List<CTRow> templateRows = new ArrayList<>();
            for (int cursor = index; cursor <= endIndex; cursor++) {
                templateRows.add((CTRow) table.getRows().get(cursor).getCtRow().copy());
            }
            for (int cursor = endIndex; cursor >= index; cursor--) {
                table.removeRow(cursor);
            }

            int insertAt = index;
            for (Object item : asList(resolve(data, collectionKey))) {
                Map<String, Object> scopedData = scopedData(data, item);
                for (CTRow templateRow : templateRows) {
                    XWPFTableRow inserted = new XWPFTableRow((CTRow) templateRow.copy(), table);
                    table.addRow(inserted, insertAt++);
                    removeLoopMarkers(inserted, collectionKey);
                    replaceRow(inserted, scopedData);
                }
            }
            index = insertAt;
        }
    }

    private int findLoopEndRow(XWPFTable table, int startIndex, String collectionKey) {
        String marker = "{{/" + collectionKey + "}}";
        Pattern looseMarker = Pattern.compile("\\{\\{/\\s*" + Pattern.quote(collectionKey) + "\\s*}}");
        for (int cursor = startIndex; cursor < table.getRows().size(); cursor++) {
            String text = rowText(table.getRows().get(cursor));
            if (text.contains(marker) || looseMarker.matcher(text).find()) {
                return cursor;
            }
        }
        return -1;
    }

    private void removeLoopMarkers(XWPFTableRow row, String collectionKey) {
        Pattern start = Pattern.compile("\\{\\{#\\s*" + Pattern.quote(collectionKey) + "\\s*}}");
        Pattern end = Pattern.compile("\\{\\{/\\s*" + Pattern.quote(collectionKey) + "\\s*}}");
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                String cleaned = end.matcher(start.matcher(text).replaceAll("")).replaceAll("");
                if (!text.equals(cleaned)) {
                    setParagraphText(paragraph, cleaned);
                }
            }
        }
    }

    private String replaceInlineLoops(String text, Map<String, Object> data) {
        String result = text;
        Matcher matcher = INLINE_LOOP.matcher(result);
        while (matcher.find()) {
            StringBuffer buffer = new StringBuffer();
            do {
                String collectionKey = matcher.group(1);
                String template = matcher.group(2);
                StringBuilder rendered = new StringBuilder();
                for (Object item : asList(resolve(data, collectionKey))) {
                    rendered.append(replacePlaceholders(template, scopedData(data, item)));
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(rendered.toString()));
            } while (matcher.find());
            matcher.appendTail(buffer);
            result = buffer.toString();
            matcher = INLINE_LOOP.matcher(result);
        }
        return result;
    }

    private String replacePlaceholders(String text, Map<String, Object> data) {
        return replaceVariables(replaceVariables(text, data, DOLLAR_VARIABLE), data, MUSTACHE_VARIABLE);
    }

    private String replaceVariables(String text, Map<String, Object> data, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = resolve(data, matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void appendParticipantTables(List<XWPFTable> tables, Map<String, Object> data) {
        if (!truthy(resolve(data, "participantTableAppend"))) {
            return;
        }
        List<Object> participants = asList(resolve(data, PARTICIPANTS_KEY));
        if (participants.isEmpty()) {
            return;
        }
        for (XWPFTable table : tables) {
            if (isParticipantTable(table)) {
                appendParticipants(table, participants);
                return;
            }
        }
    }

    private boolean isParticipantTable(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            long nameHeaders = row.getTableCells().stream()
                    .map(this::cellText)
                    .filter(text -> text.contains("姓名"))
                    .count();
            long studentNoHeaders = row.getTableCells().stream()
                    .map(this::cellText)
                    .filter(text -> text.contains("学号"))
                    .count();
            if (nameHeaders >= 1 && studentNoHeaders >= 1) {
                return true;
            }
        }
        return false;
    }

    private void appendParticipants(XWPFTable table, List<Object> participants) {
        int headerIndex = participantHeaderIndex(table);
        int participantIndex = 0;
        while (participantIndex < participants.size()) {
            Slot slot = firstEmptyParticipantSlot(table, headerIndex);
            if (slot == null) {
                appendEmptyParticipantRow(table);
                slot = firstEmptyParticipantSlot(table, headerIndex);
            }
            if (slot == null) {
                return;
            }
            fillParticipant(slot, participants.get(participantIndex++));
        }
    }

    private int participantHeaderIndex(XWPFTable table) {
        for (int index = 0; index < table.getRows().size(); index++) {
            String text = rowText(table.getRows().get(index));
            if (text.contains("姓名") && text.contains("学号")) {
                return index;
            }
        }
        return -1;
    }

    private Slot firstEmptyParticipantSlot(XWPFTable table, int headerIndex) {
        for (int rowIndex = Math.max(headerIndex + 1, 0); rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRows().get(rowIndex);
            Slot left = slot(row, 0, 1, 2);
            if (left != null && left.empty()) {
                return left;
            }
            Slot right = slot(row, 4, 5, 6);
            if (right != null && right.empty()) {
                return right;
            }
            Slot compact = slot(row, 0, 1, 2);
            if (right == null && compact != null && compact.empty()) {
                return compact;
            }
        }
        return null;
    }

    private Slot slot(XWPFTableRow row, int nameIndex, int classIndex, int studentNoIndex) {
        List<XWPFTableCell> cells = row.getTableCells();
        if (cells.size() <= Math.max(nameIndex, Math.max(classIndex, studentNoIndex))) {
            return null;
        }
        return new Slot(cells.get(nameIndex), cells.get(classIndex), cells.get(studentNoIndex));
    }

    private void appendEmptyParticipantRow(XWPFTable table) {
        if (table.getRows().isEmpty()) {
            return;
        }
        XWPFTableRow source = table.getRows().get(table.getRows().size() - 1);
        XWPFTableRow row = new XWPFTableRow((CTRow) source.getCtRow().copy(), table);
        table.addRow(row);
        clearParticipantSlots(row);
    }

    private void clearParticipantSlots(XWPFTableRow row) {
        Slot left = slot(row, 0, 1, 2);
        if (left != null) {
            left.clear();
        }
        Slot right = slot(row, 4, 5, 6);
        if (right != null) {
            right.clear();
        }
    }

    private void fillParticipant(Slot slot, Object participant) {
        setCellText(slot.nameCell(), firstText(participant, "name", "studentName"));
        setCellText(slot.classCell(), firstText(participant, "className", "class", "majorClass"));
        setCellText(slot.studentNoCell(), firstText(participant, "studentNo", "studentNumber", "no"));
    }

    private String firstText(Object source, String... keys) {
        for (String key : keys) {
            Object value = resolveFrom(source, key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private Map<String, Object> scopedData(Map<String, Object> root, Object item) {
        Map<String, Object> scoped = new LinkedHashMap<>(root == null ? Map.of() : root);
        scoped.put("this", item);
        scoped.put("item", item);
        if (item instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    scoped.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return scoped;
    }

    private Object resolve(Map<String, Object> data, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if ("this".equals(path) || "item".equals(path)) {
            return data.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            current = resolveFrom(current, part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Object resolveFrom(Object source, String key) {
        if (source == null || key == null || key.isBlank()) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if ("this".equals(key) || "item".equals(key)) {
            return source;
        }
        try {
            Method accessor = source.getClass().getMethod(key);
            if (accessor.getParameterCount() == 0) {
                return accessor.invoke(source);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        String getter = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        try {
            Method accessor = source.getClass().getMethod(getter);
            if (accessor.getParameterCount() == 0) {
                return accessor.invoke(source);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private List<Object> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(result::add);
            return result;
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                result.add(Array.get(value, index));
            }
            return result;
        }
        return List.of();
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String rowText(XWPFTableRow row) {
        StringBuilder text = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            text.append(cellText(cell)).append('\n');
        }
        return text.toString();
    }

    private String cellText(XWPFTableCell cell) {
        String text = cell.getTextRecursively();
        return text == null ? "" : text.trim();
    }

    private void setCellText(XWPFTableCell cell, String text) {
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        if (paragraphs == null || paragraphs.isEmpty()) {
            cell.setText(text == null ? "" : text);
            return;
        }
        for (int index = paragraphs.size() - 1; index >= 1; index--) {
            cell.removeParagraph(index);
        }
        setParagraphText(paragraphs.get(0), text == null ? "" : text);
    }

    private void setParagraphText(XWPFParagraph paragraph, String text) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            paragraph.createRun().setText(text == null ? "" : text);
            return;
        }
        XWPFRun firstRun = runs.get(0);
        for (int index = runs.size() - 1; index >= 1; index--) {
            paragraph.removeRun(index);
        }
        firstRun.setText(text == null ? "" : text, 0);
    }

    private record Slot(XWPFTableCell nameCell, XWPFTableCell classCell, XWPFTableCell studentNoCell) {

        boolean empty() {
            return text(nameCell).isBlank() && text(classCell).isBlank() && text(studentNoCell).isBlank();
        }

        void clear() {
            set(nameCell, "");
            set(classCell, "");
            set(studentNoCell, "");
        }

        private static String text(XWPFTableCell cell) {
            String value = cell.getTextRecursively();
            return value == null ? "" : value.trim();
        }

        private static void set(XWPFTableCell cell, String value) {
            List<XWPFParagraph> paragraphs = cell.getParagraphs();
            if (paragraphs == null || paragraphs.isEmpty()) {
                cell.setText(value);
                return;
            }
            for (int index = paragraphs.size() - 1; index >= 1; index--) {
                cell.removeParagraph(index);
            }
            XWPFParagraph paragraph = paragraphs.get(0);
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs == null || runs.isEmpty()) {
                paragraph.createRun().setText(value);
                return;
            }
            XWPFRun firstRun = runs.get(0);
            for (int index = runs.size() - 1; index >= 1; index--) {
                paragraph.removeRun(index);
            }
            firstRun.setText(value, 0);
        }
    }
}
