package online.yudream.base.interfaces.platform.feishu.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 更新文本块元素请求：elements 为飞书文本元素 JSON 原文（update_text_elements）。
 */
@Data
public class FeishuElementUpdateRequest {
    @NotEmpty(message = "至少提供一个文本元素")
    private List<String> elements;
    /** 文档版本号；为空按飞书默认最新版本处理。 */
    private Long documentRevisionId;
}
