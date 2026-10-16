package online.yudream.base.application.platform.wiki.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;

import java.io.Serializable;
import java.util.List;

/**
 * 调整空间成员授权输入。必须携带期望版本做 CAS，避免覆盖并发调整。
 */
@Data
public class WikiSpaceMemberUpdateCmd implements Serializable {
    private Long spaceId;
    private Long memberId;
    private List<WikiSpaceAclAction> actions;
    private Integer expectedVersion;
}
