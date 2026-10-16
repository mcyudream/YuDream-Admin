package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 知识库空间分页响应。
 */
@Data
@Builder
public class FeishuWikiSpacePageRes implements Serializable {
    private List<FeishuWikiSpaceRes> items;
    private String nextPageToken;
    private boolean hasMore;
}
