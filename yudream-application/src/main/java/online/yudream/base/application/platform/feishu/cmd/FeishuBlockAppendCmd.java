package online.yudream.base.application.platform.feishu.cmd;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 追加子块：children 为飞书块 JSON 原文（可由块读取接口的 raw 直接回填，保真）。
 */
@Data
@Builder
public class FeishuBlockAppendCmd {
    private String documentId;
    private String parentBlockId;
    private List<String> children;
    /** 插入位置；为空表示追加到末尾。 */
    private Integer index;
}
