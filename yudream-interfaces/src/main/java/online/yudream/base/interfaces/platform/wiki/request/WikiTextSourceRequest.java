package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 在线 Markdown 文本资料的新建/编辑请求。
 */
@Data
public class WikiTextSourceRequest {
    /** 目标目录，默认根目录。 */
    private String folderPath;
    @NotBlank(message = "资料标题不能为空")
    private String title;
    @NotBlank(message = "资料内容不能为空")
    private String content;
}
