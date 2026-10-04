package online.yudream.base.infra.platform.ai.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * AgentScope AgentState 的 MongoDB 持久化：一个 (agentId, userId, sessionId) 会话一行，
 * 单状态与状态列表分字段存放，id 为会话路径的确定性字符串，写路径天然幂等。
 */
@Data
@Document("platformAgentState")
@CompoundIndexes(@CompoundIndex(name = "agent_user_session", def = "{'agentId':1,'userId':1,'sessionId':1}", unique = true))
public class AgentStateDO {

    /** agentId/userId/sessionId 拼接的确定性主键 */
    @Id
    private String id;

    private String agentId;

    private String userId;

    private String sessionId;

    /** 单个 State 的 JSON（JsonCodec 序列化） */
    private String stateJson;

    /** State 列表的 JSON 数组 */
    private String listJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
