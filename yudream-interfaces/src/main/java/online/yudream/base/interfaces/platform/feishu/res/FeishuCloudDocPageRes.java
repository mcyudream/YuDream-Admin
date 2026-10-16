package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 云空间文件分页响应。
 */
@Data
@Builder
public class FeishuCloudDocPageRes implements Serializable {
    private List<FeishuCloudDocRes> items;
    private String nextPageToken;
    private boolean hasMore;
}
