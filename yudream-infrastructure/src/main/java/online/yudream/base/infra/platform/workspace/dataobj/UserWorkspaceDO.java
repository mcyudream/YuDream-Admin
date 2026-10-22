package online.yudream.base.infra.platform.workspace.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWorkspace")
public class UserWorkspaceDO extends BaseDO {
    /** 唯一索引由仓储启动时显式创建：每用户一个工作区 */
    private Long ownerUserId;
}
