package online.yudream.base.application.platform.feishu.cmd;

import lombok.Builder;
import lombok.Data;

/**
 * 删除父块下指定下标区间的子块（飞书 children/batch_delete）。
 * <p>
 * 区间为左闭右开 [startIndex, endIndex)，与飞书官方 batch_delete 的 start_index/end_index
 * 语义一致（官方文档：删除的起始/末尾索引，操作区间左闭右开，start_index 需要小于 end_index），
 * 原样透传不换算：删除单个块传 [i, i+1)。
 */
@Data
@Builder
public class FeishuBlockDeleteCmd {
    private String documentId;
    private String parentBlockId;
    private int startIndex;
    private int endIndex;
}
