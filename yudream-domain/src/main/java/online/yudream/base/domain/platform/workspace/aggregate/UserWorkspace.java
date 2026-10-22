package online.yudream.base.domain.platform.workspace.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

/**
 * 用户工作区：每个账号恰好一个，id 是一切路径的主键（逻辑地址 workspace://&lt;id&gt;/&lt;相对路径&gt;）。
 * 元数据在数据库，文件内容在对象存储，执行环境按需拷入临时目录。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserWorkspace extends BaseDomain {

    private Long ownerUserId;

    public static UserWorkspace create(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new BizException("工作区归属用户不能为空");
        }
        return UserWorkspace.builder().ownerUserId(ownerUserId).build();
    }

    public String uri() {
        return "workspace://" + getId() + "/";
    }
}
