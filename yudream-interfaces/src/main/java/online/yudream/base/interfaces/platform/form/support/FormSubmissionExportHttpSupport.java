package online.yudream.base.interfaces.platform.form.support;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import online.yudream.base.application.platform.form.dto.FormSubmissionExportDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionExportFileDTO;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.interfaces.platform.form.assembler.FormSubmissionExcelAssembler;
import online.yudream.base.interfaces.system.excel.support.ExcelHttpSupport;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 表单提交结果导出：无附件时直接写 Excel；存在上传附件时打包 ZIP（Excel + 附件目录），Excel 单元格仅保留文件名。
 */
public final class FormSubmissionExportHttpSupport {

    private static final String SHEET_NAME = "提交结果";
    private static final String ATTACHMENT_DIR = "附件";

    private FormSubmissionExportHttpSupport() {
    }

    public static void write(HttpServletResponse response, FormSubmissionExportDTO export,
                             Function<Long, FileContentDTO> contentLoader) throws IOException {
        String filename = FormSubmissionExcelAssembler.filename(export);
        List<List<String>> head = FormSubmissionExcelAssembler.head(export);
        List<List<Object>> rows = FormSubmissionExcelAssembler.rows(export);
        List<FormSubmissionExportFileDTO> files = export.getFiles() == null ? List.of() : export.getFiles();
        if (files.isEmpty()) {
            ExcelHttpSupport.writeDynamic(response, filename, SHEET_NAME, head, rows);
            return;
        }
        writeZip(response, filename, head, rows, files, contentLoader);
    }

    private static void writeZip(HttpServletResponse response, String filename,
                                 List<List<String>> head, List<List<Object>> rows,
                                 List<FormSubmissionExportFileDTO> files,
                                 Function<Long, FileContentDTO> contentLoader) throws IOException {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/zip");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(filename + ".xlsx"));
            EasyExcel.write(zip)
                    .head(head == null ? List.of() : head)
                    .autoCloseStream(false)
                    .sheet(SHEET_NAME)
                    .doWrite(rows == null ? List.of() : rows);
            zip.closeEntry();
            Set<String> usedPaths = new HashSet<>();
            for (FormSubmissionExportFileDTO file : files) {
                writeAttachment(zip, file, contentLoader, usedPaths);
            }
        }
    }

    private static void writeAttachment(ZipOutputStream zip, FormSubmissionExportFileDTO file,
                                        Function<Long, FileContentDTO> contentLoader, Set<String> usedPaths) throws IOException {
        FileContentDTO content;
        try {
            content = contentLoader.apply(file.getFileId());
        } catch (Exception ignored) {
            // 导出期间文件被删除或存储暂不可用时跳过该附件，不中断整体导出
            return;
        }
        String originalName = file.getOriginalName() == null || file.getOriginalName().isBlank()
                ? "file-" + file.getFileId()
                : file.getOriginalName();
        String path = uniquePath(usedPaths, ATTACHMENT_DIR + "/" + file.getSubmissionId() + "/" + sanitize(originalName));
        zip.putNextEntry(new ZipEntry(path));
        try (InputStream input = content.getInputStream()) {
            input.transferTo(zip);
        }
        zip.closeEntry();
    }

    private static String uniquePath(Set<String> usedPaths, String path) {
        String candidate = path;
        int index = 2;
        while (!usedPaths.add(candidate)) {
            int dot = path.lastIndexOf('.');
            candidate = dot > 0
                    ? path.substring(0, dot) + " (" + index + ")" + path.substring(dot)
                    : path + " (" + index + ")";
            index++;
        }
        return candidate;
    }

    private static String sanitize(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
