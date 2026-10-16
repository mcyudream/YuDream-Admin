package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 飞书文档纯文本内容响应：content 原样来自飞书 raw_content。
 */
@Data
@Builder
public class FeishuDocumentContentRes implements Serializable {
    private String documentId;
    private String content;
}
