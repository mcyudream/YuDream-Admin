package online.yudream.base.application.platform.form.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 表单提交导出附件元数据：导出时按 fileId 惰性读取文件内容打包。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSubmissionExportFileDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long submissionId;
    private String field;
    private Long fileId;
    private String originalName;
}
