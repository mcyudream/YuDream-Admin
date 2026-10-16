package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 知识库节点分页响应。
 */
@Data
@Builder
public class FeishuWikiNodePageRes implements Serializable {
    private String spaceId;
    private List<FeishuWikiNodeRes> items;
    private String nextPageToken;
    private boolean hasMore;
}
