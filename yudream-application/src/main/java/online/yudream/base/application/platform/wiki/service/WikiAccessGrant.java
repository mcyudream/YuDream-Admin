package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;

/**
 * 双闸门校验结果：命中实例 ACL 的空间与当前认证主体。
 * <p>
 * 供应用层用例复用（例如以登录用户写入创建者/审核人字段），不对外暴露。
 */
public record WikiAccessGrant(WikiSpace space, WikiActor actor) {
}
