package online.yudream.base.domain.platform.ai.valobj;

/** 私有持久寻址信息；不包含凭据、模型请求或权限快照。 */
public record HarnessRunReference(String agentCode, String sessionId, String turnId, String runId) { }
