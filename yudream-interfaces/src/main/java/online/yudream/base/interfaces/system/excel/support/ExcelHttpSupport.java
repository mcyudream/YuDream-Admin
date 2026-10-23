package online.yudream.base.interfaces.system.excel.support;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.system.excel.res.ExcelImportResultRes;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * 按单资源工作表读取全部行并一次性交给应用层批处理。层级排序、关联预检与状态二阶段写入
     * 必须归应用服务，不能在 Controller 或逐行回调中隐式依赖 Excel 行顺序。
     */
    public static <T, C> ExcelImportResultRes importBatch(MultipartFile file, Class<T> rowClass,
                                                           Function<T, C> mapper,
                                                           Function<List<C>, ExcelImportResultRes> importer) throws IOException {
        List<T> rows = readRows(file, null, rowClass);
        List<C> commands = new ArrayList<>();
        ExcelImportResultRes parseResult = ExcelImportResultRes.builder().total(rows.size()).build();
        for (int index = 0; index < rows.size(); index++) {
            try {
                commands.add(mapper.apply(rows.get(index)));
            }
            catch (RuntimeException e) {
                parseResult.setFailed(parseResult.getFailed() + 1);
                parseResult.getErrors().add("第 " + (index + 2) + " 行：" + messageOf(e));
            }
        }
        if (!parseResult.getErrors().isEmpty()) {
            parseResult.setSkipped(Math.max(0, rows.size() - parseResult.getFailed()));
            return parseResult;
        }
        try {
            return importer.apply(commands);
        }
        catch (BizException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new BizException("模板文件解析失败：" + messageOf(e));
        }
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
}
