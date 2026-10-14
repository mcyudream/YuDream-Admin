package online.yudream.base.domain.platform.ai.valobj;

import java.time.Instant;

/** 匿名公开问答的单会话观察授权。数据库仅保存随机令牌的摘要。 */
public record PublicAiRunGrant(String runId, String tokenHash, String sessionId, String spaceSlug, Instant expiresAt) { }
