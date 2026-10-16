package online.yudream.base.application.platform.wiki.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;

import java.io.Serializable;
import java.util.List;

/**
 * 新增空间成员授权输入。主体必须真实存在，动作至少一项。
 */
@Data
public class WikiSpaceMemberSaveCmd implements Serializable {
    private Long spaceId;
    private WikiSpaceMemberSubjectType subjectType;
    private Long subjectId;
    private List<WikiSpaceAclAction> actions;
}
