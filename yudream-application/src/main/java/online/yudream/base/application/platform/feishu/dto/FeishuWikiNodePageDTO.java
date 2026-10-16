package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 知识库节点分页结果。
 */
@Data
@Builder
public class FeishuWikiNodePageDTO {
    private String spaceId;
    private List<FeishuWikiNodeDTO> items;
    private String nextPageToken;
    private boolean hasMore;
}
