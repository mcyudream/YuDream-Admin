package online.yudream.base.interfaces.system.excel.support;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import jakarta.servlet.http.HttpServletResponse;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.system.excel.res.ExcelImportResultRes;
import online.yudream.base.interfaces.system.excel.res.ExcelImportRowResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ExcelHttpSupport {
    public static final long MAX_TEMPLATE_FILE_SIZE = 20L * 1024 * 1024;
    public static final int MAX_IMPORT_ROWS = 10_000;
    public static final int MAX_SHEET_COUNT = 8;
    private static final int MAX_CELL_TEXT_LENGTH = 20_000;

    private ExcelHttpSupport() {
    }

    public static <T> void write(HttpServletResponse response, String filename, String sheetName, Class<T> rowClass, List<T> rows) throws IOException {
        prepareDownload(response, filename);
        EasyExcel.write(response.getOutputStream(), rowClass)
                .autoCloseStream(false)
                .sheet(sheetName)
                .doWrite(rows == null ? List.of() : rows);
    }

    public static void writeDynamic(HttpServletResponse response, String filename, String sheetName,
                                    List<List<String>> head, List<List<Object>> rows) throws IOException {
        prepareDownload(response, filename);
        EasyExcel.write(response.getOutputStream())
                .head(head == null ? List.of() : head)
                .autoCloseStream(false)
                .sheet(sheetName)
                .doWrite(rows == null ? List.of() : rows);
    }

    public static void writeWorkbook(HttpServletResponse response, String filename, List<ExcelSheet<?>> sheets) throws IOException {
        prepareDownload(response, filename);
        ExcelWriter writer = EasyExcel.write(response.getOutputStream())
                .autoCloseStream(false)
                .build();
        try {
            int index = 0;
            for (ExcelSheet<?> sheet : sheets == null ? List.<ExcelSheet<?>>of() : sheets) {
                if (sheet == null || sheet.rowClass() == null || sheet.name() == null || sheet.name().isBlank()) {
                    continue;
                }
                WriteSheet writeSheet = EasyExcel.writerSheet(index++, sheet.name())
                        .head(sheet.rowClass())
                        .build();
                writer.write(sheet.rows() == null ? List.of() : sheet.rows(), writeSheet);
            }
        }
        finally {
            writer.finish();
        }
    }

    public static <T> ExcelImportResultRes importRows(MultipartFile file, Class<T> rowClass,
                                                        Function<T, ExcelImportRowResult> importer) throws IOException {
        List<T> rows = readRows(file, null, rowClass);
        ExcelImportResultRes result = ExcelImportResultRes.builder().total(rows.size()).build();
        for (int i = 0; i < rows.size(); i++) {
            try {
                result.record(importer.apply(rows.get(i)));
            }
            catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add("第 " + (i + 2) + " 行：" + messageOf(e));
            }
        }
        return result;
    }

    public static <T> List<T> readRows(MultipartFile file, String sheetName, Class<T> rowClass) throws IOException {
        inspectWorkbook(file, sheetName);
        try (var inputStream = file.getInputStream()) {
            List<T> rows = sheetName == null
                    ? EasyExcel.read(inputStream).head(rowClass).sheet().doReadSync()
                    : EasyExcel.read(inputStream).head(rowClass).sheet(sheetName).doReadSync();
            if (rows.size() > MAX_IMPORT_ROWS) {
                throw new BizException("模板数据行数超过限制");
            }
            return rows;
        }
        catch (BizException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new BizException("模板文件解析失败：" + messageOf(e));
        }
    }

    /**
     * 读取指定的多工作表。每个工作表按其名称和行模型严格解析，调用方负责进一步的业务预检。
     */
    public static Map<String, List<?>> readWorkbook(MultipartFile file, List<ExcelSheet<?>> sheets) throws IOException {
        requireTemplateWorkbook(file);
        List<ExcelSheet<?>> definitions = sheets == null ? List.of() : sheets;
        if (definitions.isEmpty()) {
            throw new BizException("模板工作表定义不能为空");
        }
        inspectWorkbookLayout(file, definitions);
        Map<String, List<?>> result = new LinkedHashMap<>();
        for (ExcelSheet<?> sheet : definitions) {
            if (sheet == null || sheet.name() == null || sheet.name().isBlank() || sheet.rowClass() == null) {
                throw new BizException("模板工作表定义无效");
            }
            result.put(sheet.name(), readRows(file, sheet.name(), sheet.rowClass()));
        }
        return result;
    }

    private static void inspectWorkbookLayout(MultipartFile file, List<ExcelSheet<?>> definitions) throws IOException {
        try (var inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() != definitions.size()) {
                throw new BizException("模板工作表数量不符合协议要求");
            }
            java.util.Set<String> expected = new java.util.HashSet<>();
            for (ExcelSheet<?> definition : definitions) {
                if (definition == null || definition.name() == null || definition.name().isBlank() || !expected.add(definition.name())) {
                    throw new BizException("模板工作表定义无效");
                }
            }
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                if (!expected.contains(workbook.getSheetName(index))) {
                    throw new BizException("模板包含不受支持的工作表：" + workbook.getSheetName(index));
                }
            }
        }
        catch (BizException e) {
            throw e;
        }
        catch (Exception e) {
            throw new BizException("模板文件解析失败：" + messageOf(e));
        }
    }

    private static void inspectWorkbook(MultipartFile file, String expectedSheetName) throws IOException {
        requireWorkbook(file);
        try (var inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() > MAX_SHEET_COUNT) {
                throw new BizException("模板工作表数量超过限制");
            }
            if (expectedSheetName != null && workbook.getSheet(expectedSheetName) == null) {
                throw new BizException("模板缺少工作表：" + expectedSheetName);
            }
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(sheetIndex);
                int rowCount = 0;
                for (Row row : sheet) {
                    if (++rowCount > MAX_IMPORT_ROWS + 1) {
                        throw new BizException("工作表「" + sheet.getSheetName() + "」数据行数超过限制");
                    }
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.FORMULA) {
                            throw new BizException("工作表「" + sheet.getSheetName() + "」不允许包含公式单元格");
                        }
                        if (cell.toString().length() > MAX_CELL_TEXT_LENGTH) {
                            throw new BizException("工作表「" + sheet.getSheetName() + "」存在过长单元格内容");
                        }
                    }
                }
            }
        }
        catch (BizException e) {
            throw e;
        }
        catch (Exception e) {
            throw new BizException("模板文件解析失败：" + messageOf(e));
        }
    }

    private static void prepareDownload(HttpServletResponse response, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded + ".xlsx");
    }

    private static void requireTemplateWorkbook(MultipartFile file) {
        requireWorkbook(file);
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException("系统数据模板包仅支持 .xlsx 文件");
        }
    }

    private static void requireWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择 Excel 模板文件");
        }
        if (file.getSize() > MAX_TEMPLATE_FILE_SIZE) {
            throw new BizException("模板文件超过 20MB 限制");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")
                || filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xls"))) {
            throw new BizException("仅支持 .xlsx 或 .xls Excel 文件");
        }
    }

    private static String messageOf(Exception e) {
        Throwable current = e;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank() ? "导入失败" : current.getMessage();
    }

    public record ExcelSheet<T>(String name, Class<T> rowClass, List<T> rows) {
        public ExcelSheet(String name, Class<T> rowClass) {
            this(name, rowClass, List.of());
        }
    }
}
