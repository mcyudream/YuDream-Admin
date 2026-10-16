package online.yudream.base.interfaces.platform.feishu.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 追加子块请求：children 为飞书块 JSON 原文，可把块读取接口返回的 raw 直接回填。
 */
@Data
public class FeishuBlockAppendRequest {
    @NotEmpty(message = "至少提供一个子块内容")
    private List<String> children;
    /** 插入位置；为空表示追加到末尾。 */
    private Integer index;
}
