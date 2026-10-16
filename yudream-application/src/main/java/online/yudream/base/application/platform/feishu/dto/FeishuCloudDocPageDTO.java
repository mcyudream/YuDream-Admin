package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 云空间文件分页结果。
 */
@Data
@Builder
public class FeishuCloudDocPageDTO {
    private List<FeishuCloudDocDTO> items;
    private String nextPageToken;
    private boolean hasMore;
}
