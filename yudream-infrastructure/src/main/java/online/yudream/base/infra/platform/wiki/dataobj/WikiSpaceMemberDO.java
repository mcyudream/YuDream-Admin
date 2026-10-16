package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库空间成员授权（实例 ACL）。
 * <p>
 * spaceId 一律以字段表达，不拼接进 permission code；同一空间同一主体唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWikiSpaceMember")
@CompoundIndex(name = "spaceSubject", def = "{'spaceId':1,'subjectType':1,'subjectId':1}", unique = true)
public class WikiSpaceMemberDO extends BaseDO {
    private Long spaceId;
    private WikiSpaceMemberSubjectType subjectType;
    private Long subjectId;
    private List<WikiSpaceAclAction> actions = new ArrayList<>();
    private Long createdByUserId;
}
