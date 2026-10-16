package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 公开端知识库资料响应：只包含安全封面字段与评论开关，不含文件对象 ID、ACL 模式等内部信息。
 */
@Data
@Builder
public class WikiPublicSpaceProfileRes implements Serializable {
    private String name;
    private String slug;
    private String description;
    private String coverType;
    private String coverSeed;
    private String coverUrl;
    private String coverAlt;
    private boolean commentEnabled;
}
