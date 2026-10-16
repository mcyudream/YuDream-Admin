package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 知识库空间分页结果。
 */
@Data
@Builder
public class FeishuWikiSpacePageDTO {
    private List<FeishuWikiSpaceDTO> items;
    private String nextPageToken;
    private boolean hasMore;
}
