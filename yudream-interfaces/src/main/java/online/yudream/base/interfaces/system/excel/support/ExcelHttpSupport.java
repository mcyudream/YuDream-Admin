package online.yudream.base.interfaces.system.excel.support;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import online.yudream.base.interfaces.system.excel.res.ExcelImportResultRes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public final class ExcelHttpSupport {
    private ExcelHttpSupport() {
    }

    public static <T> void write(HttpServletResponse response, String filename, String sheetName, Class<T> rowClass, List<T> rows) throws IOException {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded + ".xlsx");
        EasyExcel.write(response.getOutputStream(), rowClass)
                .autoCloseStream(false)
                .sheet(sheetName)
                .doWrite(rows == null ? List.of() : rows);
    }

    public static <T> ExcelImportResultRes importRows(MultipartFile file, Class<T> rowClass, Consumer<T> importer) throws IOException {
        List<T> rows = EasyExcel.read(file.getInputStream()).head(rowClass).sheet().doReadSync();
        ExcelImportResultRes result = ExcelImportResultRes.builder().total(rows.size()).build();
        for (int i = 0; i < rows.size(); i++) {
            try {
                importer.accept(rows.get(i));
                result.setSuccess(result.getSuccess() + 1);
            }
            catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add("第 " + (i + 2) + " 行：" + e.getMessage());
            }
        }
        return result;
    }
}
