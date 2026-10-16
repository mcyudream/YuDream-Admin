package online.yudream.base.interfaces.platform.feishu.request;

import lombok.Data;

/**
 * 删除子块请求：删除父块下 [startIndex, endIndex) 区间的子块。
 * <p>
 * 左闭右开，与飞书 batch_delete 官方语义一致；删除单个块传 [i, i+1)，后端不做下标换算。
 */
@Data
public class FeishuBlockDeleteRequest {
    private int startIndex;
    private int endIndex;
}
