package online.yudream.base.interfaces.platform.form.assembler;

import online.yudream.base.application.platform.form.dto.DynamicFormDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionExportDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionExportFileDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormSubmissionExcelAssemblerTest {

    private static final String SCHEMA = """
            [
              {"type": "input", "field": "name", "title": "姓名"},
              {"type": "upload", "field": "attachment", "title": "附件"}
            ]
            """;

    @Test
    void mapsUploadFieldValuesToOriginalFilenames() {
        FormSubmissionExportDTO export = export(List.of(
                FormSubmissionExportFileDTO.builder()
                        .submissionId(11L).field("attachment").fileId(101L).originalName("需求文档.docx")
                        .build(),
                FormSubmissionExportFileDTO.builder()
                        .submissionId(11L).field("attachment").fileId(102L).originalName("截图.png")
                        .build()
        ));

        List<List<Object>> rows = FormSubmissionExcelAssembler.rows(export);

        assertEquals(1, rows.size());
        List<Object> row = rows.get(0);
        assertEquals("张三", row.get(1));
        assertEquals("需求文档.docx, 截图.png", row.get(2));
    }

    @Test
    void keepsRawValueWhenFileMetadataMissing() {
        FormSubmissionExportDTO export = export(List.of());

        List<List<Object>> rows = FormSubmissionExcelAssembler.rows(export);

        assertEquals("/api/files/101/content, /api/files/102/content", rows.get(0).get(2));
    }

    @Test
    void headContainsFieldTitles() {
        List<List<String>> head = FormSubmissionExcelAssembler.head(export(List.of()));
        assertEquals(List.of("提交 ID"), head.get(0));
        assertEquals(List.of("姓名"), head.get(1));
        assertEquals(List.of("附件"), head.get(2));
    }

    private FormSubmissionExportDTO export(List<FormSubmissionExportFileDTO> files) {
        return FormSubmissionExportDTO.builder()
                .form(DynamicFormDTO.builder().name("反馈收集").schemaJson(SCHEMA).build())
                .submissions(List.of(FormSubmissionDTO.builder()
                        .id(11L)
                        .data(Map.of(
                                "name", "张三",
                                "attachment", List.of("/api/files/101/content", "/api/files/102/content")))
                        .build()))
                .files(files)
                .build();
    }
}
