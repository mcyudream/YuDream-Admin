package online.yudream.base.domain.platform.feishu.service;

/**
 * 飞书用例的认证主体来源：身份只从当前会话（Sa-Token 登录态或 API Key 上下文）推导，
 * 前端传入的任何用户标识都不作为归属依据。
 */
public interface FeishuActorGateway {

    /** 当前登录用户 ID；未登录返回 null。 */
    Long currentUserId();
}
